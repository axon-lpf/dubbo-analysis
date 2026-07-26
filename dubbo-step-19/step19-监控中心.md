# Step 19：监控中心（MonitorFilter）

## 一、本步骤解决的问题

**核心问题：如何在不修改任何业务代码的情况下，收集和展示 RPC 调用数据？**

有了 Filter SPI，答案变得极其简单：写一个 MonitorFilter，通过 @Activate 自动加入 Filter 链，即可收集所有 RPC 调用的统计信息。

## 二、新增了哪些能力

- ✅ `MonitorFilter` — 调用统计过滤器（@Activate provider + consumer）
- ✅ 按接口.方法 分组统计：次数、成功/失败、平均/最大/最小耗时
- ✅ `MonitorFilter.getSummary()` — 随时打印统计摘要

## 三、核心类一览

| 类名 | 作用 | Dubbo 对应 |
|------|------|-----------|
| `MonitorFilter` | 监控数据收集（@Activate） | `org.apache.dubbo.monitor.support.MonitorFilter` |

## 四、核心原理剖析

### 4.1 零侵入的监控——Filter SPI 的威力

```
添加监控前后对比：

添加前：
  Protocol → DubboInvoker → 网络 → 业务代码

添加后（不改一行业务代码！）：
  Protocol → [MonitorFilter] → DubboInvoker → 网络 → 业务代码
                │
                └── 记录: 调用次数 +1, 耗时 +XXms, 成功/失败
```

只需三步添加监控：
```properties
# 1. 实现 MonitorFilter implements Filter
# 2. @Activate(group = {"provider", "consumer"})
# 3. SPI 配置加一行:
monitor=com.axon.dubbo.rpc.filter.MonitorFilter
```

### 4.2 统计数据结构

```java
// 按 "接口名.方法名" 分组
Map<String, MethodStats> STATS

MethodStats {
    totalCount:    AtomicLong  // 总调用次数
    successCount:  AtomicLong  // 成功次数
    failCount:     AtomicLong  // 失败次数
    totalElapsed:  AtomicLong  // 总耗时
    maxElapsed:    long        // 最大耗时
    minElapsed:    long        // 最小耗时
}
```

### 4.3 统计收集时序

```
MonitorFilter.invoke(invoker, invocation)
  │
  ├─ start = now()
  │
  ├─ result = invoker.invoke(invocation)
  │     │
  │     └── 实际 RPC 调用
  │
  ├─ elapsed = now() - start
  │
  └─ finally:
       STATS["接口.方法"].record(success, elapsed)
         ├─ totalCount++
         ├─ successCount++ or failCount++
         ├─ totalElapsed += elapsed
         ├─ maxElapsed = max(maxElapsed, elapsed)
         └─ minElapsed = min(minElapsed, elapsed)
```

### 4.4 统计输出示例

```
========== Monitor 统计 ==========
  com.axon.demo.IUserService.getUser        总:   5  成功:   5  失败: 0  平均:  2ms  最大: 15ms  最小:  1ms
  com.axon.demo.IUserService.getUserName    总:   3  成功:   3  失败: 0  平均:  1ms  最大:  3ms  最小:  1ms
  com.axon.demo.IUserService.saveUser       总:   2  成功:   2  失败: 0  平均:  3ms  最大:  5ms  最小:  1ms
==================================
```

### 4.5 @Activate 两端激活

```java
@Activate(group = {"provider", "consumer"}, order = Integer.MAX_VALUE)
public class MonitorFilter implements Filter { ... }
```

- `group = {"provider", "consumer"}` → 在两端都激活
- `order = Integer.MAX_VALUE` → 排到 Filter 链末尾（确保其他 Filter 先执行）

这样 Provider 端和 Consumer 端各自统计自己的调用数据，互不干扰。

## 五、面试常见问法

**Q: Dubbo 的监控是如何实现的？**
A: 通过 MonitorFilter（Filter SPI 扩展）。它在 Provider 和 Consumer 端自动激活，每次 RPC 调用经过时收集统计信息（次数、耗时、成功/失败），汇总后上报到监控中心。

**Q: 如何在不修改业务代码的情况下添加监控？**
A: Dubbo 的 Filter SPI 机制让监控成为"可插拔"组件。实现 MonitorFilter → @Activate 声明 → SPI 配置文件注册，框架自动将其加入调用链。业务代码、协议层、代理层完全不需要修改。

## 六、快速复习入口

| 想了解的内容 | 文件路径 |
|-------------|---------|
| MonitorFilter | `src/main/java/.../rpc/filter/MonitorFilter.java` |
| Filter SPI 配置 | `src/main/resources/META-INF/dubbo/internal/com.axon.dubbo.rpc.Filter` |
| 单元测试 | `src/test/java/.../demo/ApiTest.java` |
