# Step 11：集群调用器（Cluster / FailoverClusterInvoker）

## 一、本步骤解决的问题

**核心问题：如何将多个 Provider + LoadBalance + 容错组合成一个统一的调用入口？**

前面的步骤分别实现了 Directory（服务列表）、LoadBalance（选择策略），但它们还分散在各处。Step 11 引入 Cluster 层，将这些组件整合为一个"集群 Invoker"：

```
Step 10（分散）:                          Step 11（聚合）:
═════════════                            ════════════

directory.list() → 手动选第一个              Cluster.join(directory)
  或 loadBalance.select() → 手动调用           → ClusterInvoker.invoke()
                                                  ├── Directory.list()
                                                  ├── LoadBalance.select()
                                                  ├── Invoker.invoke()
                                                  └── retry on failure
```

## 二、新增了哪些能力

- ✅ `Cluster` — 集群接口（join Directory → 集群 Invoker）
- ✅ `AbstractClusterInvoker<T>` — 集群 Invoker 抽象基类
- ✅ `FailoverClusterInvoker<T>` — 失败自动切换（retry）
- ✅ `FailoverCluster` — Failover 集群工厂
- ✅ `DubboProtocol` 升级 — refer() 通过 Cluster.join() 返回集群 Invoker

## 三、核心类一览

| 类名 | 作用 | Dubbo 对应 |
|------|------|-----------|
| `Cluster` | 集群接口 | `org.apache.dubbo.rpc.cluster.Cluster` |
| `AbstractClusterInvoker<T>` | 集群 Invoker 基类 | `org.apache.dubbo.rpc.cluster.support.AbstractClusterInvoker` |
| `FailoverClusterInvoker<T>` | 失败切换 | `org.apache.dubbo.rpc.cluster.support.FailoverClusterInvoker` |
| `FailoverCluster` | Failover 工厂 | `org.apache.dubbo.rpc.cluster.support.FailoverCluster` |

## 四、核心原理剖析

### 4.1 Cluster 的定位

```
                    ┌──────────────────┐
                    │     Cluster      │
                    │  join(Directory) │
                    └────────┬─────────┘
                             │
              ┌──────────────┴──────────────┐
              │      ClusterInvoker         │
              │                             │
              │  doInvoke(invocation):      │
              │    1. directory.list()      │ ← 获取 Provider 列表
              │    2. loadBalance.select()  │ ← 选一个
              │    3. invoker.invoke()      │ ← 执行调用
              │    4. if fail → retry       │ ← 容错
              └─────────────────────────────┘
```

Cluster 层在 Dubbo 架构中的位置：

```
Consumer 端调用链：
  Proxy → ClusterInvoker → Directory → LoadBalance → Invoker → Protocol → Transport
```

### 4.2 AbstractClusterInvoker 设计

```java
public abstract class AbstractClusterInvoker<T> extends AbstractInvoker<T> {
    protected final Directory<T> directory;   // 服务目录
    protected final LoadBalance loadBalance;  // 负载均衡

    // 从 Directory 获取 Invoker 列表
    protected List<Invoker<T>> list(Invocation inv) {
        return directory.list(inv);
    }

    // 通过 LoadBalance 选择一个
    protected Invoker<T> select(Invocation inv) {
        return loadBalance.select(list(inv), getUrl(), inv);
    }

    // 子类实现具体的容错逻辑
    protected abstract Result doInvoke(Invocation inv) throws Throwable;
}
```

### 4.3 FailoverClusterInvoker 的重试机制

```
3 个 Provider (P1, P2, P3)，retries=2

调用流程：
  ┌─ 第1次: select({P1,P2,P3}) → P1 → invoke() → 失败
  │
  ├─ 第2次: select({P2,P3}) → P2 → invoke() → 失败
  │  （P1 已排除，避免重复重试同一个失败的 Provider）
  │
  └─ 第3次: select({P3}) → P3 → invoke() → 成功 ✓！
     返回结果

如果 P3 也失败 → 抛出"全部 retries+1 次尝试失败"异常
```

```java
// 关键：排除已失败的 Invoker
List<Invoker<T>> candidates = new ArrayList<>(invokers);
candidates.removeAll(invoked);  // 不移除已失败的

if (candidates.isEmpty()) {
    candidates = invokers;  // 全部失败 → 重新全部选中（最后一次机会）
}

return loadBalance.select(candidates, getUrl(), invocation);
```

### 4.4 完整的 Consumer 端调用链

```
userService.getUser(1001L)                        // 业务代码
  → InvokerInvocationHandler.invoke()             // JDK 代理拦截
    → clusterInvoker.invoke(invocation)            // 集群入口
      → AbstractClusterInvoker.invoke()            // 模板方法
        → FailoverClusterInvoker.doInvoke()        // 容错逻辑
          → directory.list(invocation)             // 获取 [P1, P2, P3]
          → loadBalance.select(invokers, ...)      // 选 P1
          → invoker.invoke(invocation)             // DubboInvoker → 网络调用
          → if fail → retry with P2               // 容错
          → return Result                         // 返回
    → result.recreate()                            // 提取值/异常
  → return user                                    // 业务代码拿到结果
```

### 4.5 DubboProtocol.refer() 的变化

```java
// Step 10: 手动选第一个
List<Invoker<T>> invokers = directory.list(null);
return invokers.get(0);

// Step 11: Cluster 自动管理
Cluster cluster = new FailoverCluster();
return cluster.join(directory);
// → FailoverClusterInvoker
//   ├── Directory（动态 Provider 列表）
//   ├── LoadBalance（加权随机选择）
//   └── Retry（失败自动切换）
```

## 五、面试常见问法

**Q: Dubbo 的 Cluster 层是做什么的？**
A: Cluster 层将多个 Provider Invoker 封装为一个逻辑 Invoker，内部集成负载均衡和容错策略。对上层（Proxy）来说，ClusterInvoker 就是一个普通的 Invoker，完全不感知底层有多个 Provider。

**Q: FailoverClusterInvoker 的重试会重复调用同一个 Provider 吗？**
A: 不会。FailoverClusterInvoker 维护了 `invoked` 列表，每次重试会排除已失败的 Provider，从剩余的中选择新的。如果全部 Provider 都失败，会抛出包含所有异常的 RuntimeException。

**Q: ClusterInvoker 和普通 Invoker 有什么不同？**
A: 普通 Invoker（如 DubboInvoker）的 doInvoke 是一次网络调用。ClusterInvoker 的 doInvoke 是多次尝试：它先通过 Directory 获取 Provider 列表，再通过 LoadBalance 选择，失败后自动切换到下一个。对调用方来说，两者接口完全一致（都是 Invoker），这就是"面向接口编程"的优势。

## 六、快速复习入口

| 想了解的内容 | 文件路径 |
|-------------|---------|
| 集群接口 | `src/main/java/.../rpc/cluster/Cluster.java` |
| 集群 Invoker 基类 | `src/main/java/.../rpc/cluster/support/AbstractClusterInvoker.java` |
| 失败切换实现 | `src/main/java/.../rpc/cluster/support/FailoverClusterInvoker.java` |
| Failover 工厂 | `src/main/java/.../rpc/cluster/support/FailoverCluster.java` |
| 集成 Cluster 的协议 | `src/main/java/.../rpc/protocol/DubboProtocol.java` |
| 单元测试 | `src/test/java/.../demo/ApiTest.java` |
