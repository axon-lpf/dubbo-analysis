# Step 12：容错策略全集

## 一、本步骤解决的问题

**核心问题：调用失败时如何处理？不同的业务场景需要不同的容错策略。**

Step 11 只实现了 Failover（失败重试）一种策略。Step 12 补齐全部 6 种经典容错策略。

## 二、新增了哪些能力

- ✅ `FailfastClusterInvoker` — 快速失败（只调一次）
- ✅ `FailsafeClusterInvoker` — 安全失败（吞掉异常）
- ✅ `ForkingClusterInvoker` — 并行调用（并发多路）
- ✅ `BroadcastClusterInvoker` — 广播调用（逐一遍历）
- ✅ `FailbackClusterInvoker` — 失败恢复（后台重试）
- ✅ `AvailableClusterInvoker` — 可用性检查（找第一个可用的）

## 三、核心类一览

| 类名 | 策略 | 适用场景 |
|------|------|---------|
| `FailoverClusterInvoker` | 失败自动切换+重试 | 读操作（幂等） |
| `FailfastClusterInvoker` | 快速失败，不重试 | **写操作（非幂等）** |
| `FailsafeClusterInvoker` | 吞掉异常，返回空 | 日志、监控旁路 |
| `ForkingClusterInvoker` | 并发调用，取最快 | **实时性要求高的读** |
| `BroadcastClusterInvoker` | 逐个调用所有 Provider | 缓存刷新、配置通知 |
| `FailbackClusterInvoker` | 失败后后台定时重试 | 消息通知 |
| `AvailableClusterInvoker` | 找第一个可用的 | 无需负载均衡 |

## 四、核心原理剖析

### 4.1 Failfast — 快速失败

```
只调一次，不重试，失败直接抛异常。

P1 调用 → 失败 → 立即抛出 RuntimeException("Failfast: 调用失败 → P1")
        → 不会尝试 P2、P3

适用：insert/delete/update 等非幂等操作，重试可能造成重复数据。
```

### 4.2 Failsafe — 安全失败

```
P1 调用 → 失败 → 记录日志 → 返回 null（空结果）
        → 调用方拿到 null，但不感知异常

适用：日志上报、监控埋点等不影响主流程的旁路调用。
```

### 4.3 Forking — 并行调用

```
同时向 N 个 Provider 发送请求，取第一个成功结果。

P1 ──→ (500ms 后返回成功) ←── 使用这个结果
P2 ──→ (超时)
P3 ──→ (200ms 失败)

总耗时 = 500ms（而不是逐个尝试的 500+超时+200+重试间隔）
适用：对延迟敏感的读操作，用冗余换取低延迟。
```

```java
CompletionService<Result> cs = new ExecutorCompletionService<>(executor);
for (Invoker<T> inv : invokers) {
    cs.submit(() -> inv.invoke(invocation));
}
// 取第一个完成的结果
Result result = cs.take().get();
```

### 4.4 Broadcast — 广播调用

```
逐个调用所有 Provider，任一失败即失败。

P1 → 调用成功 ✓
P2 → 调用成功 ✓
P3 → 调用失败 ✗ → 停止，抛出异常

适用：缓存刷新、配置更新等需要通知所有节点的场景。
```

### 4.5 Failback — 失败后台重试

```
P1 调用 → 失败 → 立即返回 null 给调用方
                  → 放入重试队列
                  → 5 秒后后台重试
                  → 成功 → 移除队列
                  → 仍失败 → 继续等待下次重试

适用：消息通知，允许最终一致性。
```

### 4.6 策略对比总表

| 策略 | 重试 | 并行 | 忽略异常 | 调用所有 | 适用场景 |
|------|------|------|---------|---------|---------|
| Failover | ✓ | ✗ | ✗ | ✗ | 读操作（幂等） |
| Failfast | ✗ | ✗ | ✗ | ✗ | 写操作 |
| Failsafe | ✗ | ✗ | ✓ | ✗ | 旁路调用 |
| Forking | ✗ | ✓ | ✗ | ✗ | 低延迟读 |
| Broadcast | ✗ | ✗ | ✗ | ✓ | 通知所有 |
| Failback | ✓(异步) | ✗ | ✓ | ✗ | 最终一致 |
| Available | ✗ | ✗ | ✗ | ✗ | 简单可用性 |

## 五、面试常见问法

**Q: Dubbo 支持哪些集群容错策略？默认是什么？**
A: Failover（默认）、Failfast、Failsafe、Forking、Broadcast、Failback、Available。默认是 Failover。

**Q: Failover 和 Failfast 的区别？什么时候用哪个？**
A: Failover 失败后自动切换到其他 Provider 重试，适合读操作（幂等）。Failfast 只调一次失败即报错，适合写操作（非幂等，重试可能导致重复写入）。

**Q: Forking 的原理是什么？优势和风险？**
A: 并发向多个 Provider 发送请求，取第一个成功结果。优势是降低延迟，风险是浪费 Provider 资源（多个 Provider 同时执行同一个请求）。通常只 fork 前 2-3 个 Provider。

**Q: Broadcast 和逐个重试有什么区别？**
A: Broadcast 必须所有 Provider 都调用成功才算成功（任一失败即失败），适合缓存刷新等通知场景。Failover 只需一个成功即可，适合查询场景。

## 六、快速复习入口

| 文件 | 内容 |
|------|------|
| `FailfastClusterInvoker.java` | 快速失败 |
| `FailsafeClusterInvoker.java` | 安全失败 |
| `ForkingClusterInvoker.java` | 并行调用 |
| `BroadcastClusterInvoker.java` | 广播调用 |
| `FailbackClusterInvoker.java` | 失败恢复 |
| `AvailableClusterInvoker.java` | 可用性检查 |
| `ClusterFactories.java` | 工厂类集合 |
