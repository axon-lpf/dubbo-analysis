# Step 10：负载均衡策略

## 一、本步骤解决的问题

**核心问题：存在多个 Provider 时，如何选择调用的目标？**

Step 09 中 Directory 能列出所有 Provider，但 Consumer 始终手动选第一个。Step 10 引入 4 种经典的负载均衡策略，实现自动化、智能化的 Provider 选择。

## 二、新增了哪些能力

- ✅ `LoadBalance` — 负载均衡接口
- ✅ `AbstractLoadBalance` — 抽象基类（权重提取、单 Invoker 短路）
- ✅ `RandomLoadBalance` — 加权随机
- ✅ `RoundRobinLoadBalance` — 平滑加权轮询（Nginx 算法）
- ✅ `LeastActiveLoadBalance` — 最少活跃调用数
- ✅ `ConsistentHashLoadBalance` — 一致性哈希

## 三、核心类一览

| 类名 | 算法 | 适用场景 |
|------|------|---------|
| `LoadBalance` | 接口 | — |
| `AbstractLoadBalance` | 权重提取 + 模板方法 | — |
| `RandomLoadBalance` | 加权随机 | 通用（默认策略） |
| `RoundRobinLoadBalance` | 平滑加权轮询 | 请求量均匀分布 |
| `LeastActiveLoadBalance` | 最少活跃 | 自动避开慢节点 |
| `ConsistentHashLoadBalance` | MD5 哈希环 | 缓存亲和、有状态服务 |

## 四、核心原理剖析

### 4.1 加权随机（RandomLoadBalance）

```
Provider A(100) B(200) C(300)，总权重 600

权重区间：
  A: [0, 100)
  B: [100, 300)
  C: [300, 600)

算法：
  offset = random(0, 600)
  for each invoker:
      offset -= invoker.weight
      if offset < 0 → 选中！

如果所有权重相同 → random(n) 直接选（更快）
```

### 4.2 平滑加权轮询（RoundRobinLoadBalance）

Nginx 风格的加权轮询，确保分布平滑而不连续重复：

```
Provider A(5) B(1) C(1)，7 次调用：

轮次  current(A,B,C)  选中  减总权重(7)后
1     (5, 1, 1)       A     (-2, 1, 1)
2     (3, 2, 2)       A     (-4, 2, 2)
3     (1, 3, 3)       B     (1, -4, 3)
4     (6,-3, 4)       A     (-1,-3, 4)
5     (4,-2, 5)       C     (4,-2,-2)
6     (9,-1,-1)       A     (2,-1,-1)
7     (7, 0, 0)       A     (0, 0, 0)

结果：A=5次 B=1次 C=1次 ✓
分布：A B A C A A A（平滑，不重复）
```

核心逻辑：
```java
// 每次选择：
1. 所有 current += weight
2. 选 current 最大的
3. 选中者 current -= totalWeight
```

### 4.3 最少活跃（LeastActiveLoadBalance）

```
活跃数 = 正在进行的调用数

Provider A(active=0) B(active=5) C(active=5)
  → 筛选最少活跃: [A]
  → 直接返回 A

如果有多个相同最少活跃 → 加权随机
```

活跃数的意义：Provider 处理慢 → 活跃数累积高 → 新请求不再发给它 → 自动实现"慢 Provider 降权"。

### 4.4 一致性哈希（ConsistentHashLoadBalance）

```
哈希环 (TreeMap<Long, Invoker>):

         0
    ┌────┴────┐
    │    ●     │  ← Provider A (虚拟节点 ×160)
    │         │
    │  ●      │  ← Provider B
    │    \    │
    │   hash │  ← 请求参数 hash
    │    ↓   │
    │    ●   │  ← Provider C（顺时针第一个节点）
    └─────────┘
  2^32-1

关键特性：
- 相同参数 → 相同 hash → 相同 Provider ✓
- Provider 上/下线 → 仅影响相邻节点的 Key 分布
- 虚拟节点（160个）→ 解决 Hash 环倾斜问题
```

```java
// 选择逻辑
long hash = hash(buildArgumentKey(invocation));
Map.Entry<Long, Invoker> entry = ring.ceilingEntry(hash); // 顺时针查找
if (entry == null) entry = ring.firstEntry();              // 环形兜底
return entry.getValue();
```

### 4.5 策略对比

| 策略 | 权重支持 | 请求亲和 | 动态调整 | 适用场景 |
|------|---------|---------|---------|---------|
| Random | ✓ | ✗ | ✗ | 通用，默认策略 |
| RoundRobin | ✓ | ✗ | ✗ | 请求量需要均匀分布 |
| LeastActive | ✓ | ✗ | ✓(自动) | Provider 性能不均 |
| ConsistentHash | ✗ | ✓(参数级) | ✗ | 缓存亲和、有状态 |

## 五、面试常见问法

**Q: Dubbo 支持哪些负载均衡策略？默认是什么？**
A: Random（默认）、RoundRobin、LeastActive、ConsistentHash。默认是 Random（加权随机），可通过 `@Reference(loadbalance="roundrobin")` 配置。

**Q: 加权轮询的平滑算法是怎样的？**
A: 每个 Invoker 维护 currentWeight 和 staticWeight。每次选择时所有 currentWeight += staticWeight，选最大的，选中者 currentWeight -= totalWeight。这样权重高的被选次数多，但分布平滑不连续重复。

**Q: 一致性哈希有什么优缺点？**
A: 优点：相同参数的请求总是路由到同一个 Provider，适合缓存场景。缺点：Provider 上下线时部分请求会重新分配；无法利用 Provider 的权重差异。

**Q: 最少活跃策略如何知道哪个 Provider 最空闲？**
A: 通过 RpcStatus 统计每个 Provider 当前的活跃调用数（开始调用 +1，调用完成 -1）。活跃数低说明 Provider 处理快，新请求优先发给它。

## 六、快速复习入口

| 想了解的内容 | 文件路径 |
|-------------|---------|
| 负载均衡接口 | `src/main/java/.../rpc/cluster/LoadBalance.java` |
| 抽象基类 | `src/main/java/.../rpc/cluster/AbstractLoadBalance.java` |
| 加权随机 | `src/main/java/.../rpc/cluster/loadbalance/RandomLoadBalance.java` |
| 加权轮询 | `src/main/java/.../rpc/cluster/loadbalance/RoundRobinLoadBalance.java` |
| 最少活跃 | `src/main/java/.../rpc/cluster/loadbalance/LeastActiveLoadBalance.java` |
| 一致性哈希 | `src/main/java/.../rpc/cluster/loadbalance/ConsistentHashLoadBalance.java` |
| 单元测试 | `src/test/java/.../demo/ApiTest.java` |
