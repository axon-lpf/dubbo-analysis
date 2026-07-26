# Step 09：多提供者与服务目录

## 一、本步骤解决的问题

**核心问题：当一个服务有多个 Provider 时，Consumer 如何管理它们？**

前面的步骤中，虽然 Directory 设计上支持多 Invoker，但没有真正测试过多 Provider 场景。Step 09 正式引入多 Provider 支持：

1. 多个 Provider 同时注册同一个服务
2. Directory 正确列出所有可用 Invoker
3. Provider 动态上下线时 Directory 自动增删

## 二、新增了哪些能力

- ✅ `AbstractDirectory<T>` — 目录抽象基类（统一 invokers 管理）
- ✅ `StaticDirectory<T>` — 静态目录（手动指定 Invoker 列表，不走注册中心）
- ✅ `RegistryDirectory<T>` 重构 — 继承 AbstractDirectory
- ✅ `DubboProtocol` 升级 — 展示 Directory 中全部 Invoker

## 三、核心类一览

| 类名 | 作用 | Dubbo 对应 |
|------|------|-----------|
| `AbstractDirectory<T>` | 目录抽象基类 | `org.apache.dubbo.rpc.cluster.directory.AbstractDirectory` |
| `StaticDirectory<T>` | 静态目录（固定 Invoker） | `org.apache.dubbo.rpc.cluster.directory.StaticDirectory` |
| `RegistryDirectory<T>` (重构) | 动态目录（继承 AbstractDirectory） | `org.apache.dubbo.registry.integration.RegistryDirectory` |

## 四、核心原理剖析

### 4.1 Directory 类层次

```
                    <<interface>>
                    Directory<T>
                         │
                         │  list(Invocation): List<Invoker<T>>
                         │  getInterface(): Class<T>
                         │
                  AbstractDirectory<T>
                   │  invokers: volatile List<Invoker<T>>
                   │  setInvokers(List)
                   │
         ┌─────────┴──────────┐
         │                    │
  RegistryDirectory     StaticDirectory
  (注册中心订阅)          (手动指定列表)
  ┌────────────────┐    ┌────────────────┐
  │ subscribe()     │    │ 构造时传入      │
  │ notify(urls)    │    │ 固定的 Invoker  │
  │   → setInvokers │    │ 列表，不再变化   │
  └────────────────┘    └────────────────┘
```

### 4.2 多 Provider 动态感知时序

```
Registry                      RegistryDirectory               Consumer
   │                                  │                           │
   │ Provider1 注册                    │                           │
   │── notify([P1]) ───────────────→   │                           │
   │                    setInvokers([I1])                          │
   │                                  │                           │
   │ Provider2 注册                    │                           │
   │── notify([P1,P2]) ────────────→   │                           │
   │                    setInvokers([I1,I2])                      │
   │                                  │                           │
   │ Provider3 注册                    │                           │
   │── notify([P1,P2,P3]) ─────────→   │                           │
   │                    setInvokers([I1,I2,I3])                   │
   │                                  │                           │
   │                                  │  list() → [I1, I2, I3]    │
   │                                  │  ─────────────────────→   │
   │                                  │                           │
   │ Provider2 下线                    │                           │
   │── notify([P1,P3]) ───────────→   │                           │
   │                    setInvokers([I1,I3])                      │
   │                                  │                           │
   │                                  │  list() → [I1, I3]        │
   │                                  │  ─────────────────────→   │
```

### 4.3 StaticDirectory 使用场景

```java
// 直连模式——跳过注册中心，直接连接 Provider
// 适用场景：开发调试、单元测试、不需要服务发现的简单应用

DubboCodec codec = new DubboCodec();
List<Invoker<IUserService>> invokers = Arrays.asList(
    new DubboInvoker<>(IUserService.class,
        URL.builder().host("10.0.0.1").port(20880)
            .path(IUserService.class.getName()).build(), codec),
    new DubboInvoker<>(IUserService.class,
        URL.builder().host("10.0.0.2").port(20880)
            .path(IUserService.class.getName()).build(), codec)
);

StaticDirectory<IUserService> directory = new StaticDirectory<>(
    IUserService.class, consumerUrl, invokers);

// 不依赖任何注册中心，直接使用固定的 Provider 列表
List<Invoker<IUserService>> providers = directory.list(null);
```

### 4.4 AbstractDirectory 的 volatile 设计

```java
protected volatile List<Invoker<T>> invokers = Collections.emptyList();

// setInvokers 用不可变列表确保线程安全
protected void setInvokers(List<Invoker<T>> newInvokers) {
    this.invokers = Collections.unmodifiableList(newInvokers);
}
```

`volatile` + `unmodifiableList` 的组合：
- **volatile**：保证写操作（notify 线程）对读操作（业务线程）立即可见
- **unmodifiableList**：防止外部代码误修改内部列表
- **全量替换**：不修改 List 内部元素，而是整体替换引用

### 4.5 DubboProtocol 的多 Provider 展示

```java
List<Invoker<T>> invokers = directory.list(null);
// Step 09: 打印所有 Provider 信息
System.out.println("从 Directory 获取到 " + invokers.size() + " 个 Invoker");
for (int i = 0; i < invokers.size(); i++) {
    System.out.println("  [" + i + "] " + invokers.get(i).getUrl().getAddress());
}
// Step 09: 手动选第一个 → Step 10: LoadBalance 自动选择
return invokers.get(0);
```

## 五、面试常见问法

**Q: Directory 在 Dubbo 中有什么作用？**
A: Directory 是 Consumer 端的服务目录，缓存了从注册中心获取的 Provider Invoker 列表。它实现了 NotifyListener，在 Provider 上下线时自动刷新列表。是 Cluster 层（负载均衡、路由、容错）的数据源。

**Q: StaticDirectory 和 RegistryDirectory 有什么区别？**
A: StaticDirectory 的 Invoker 列表是创建时指定的，之后不再变化，适用于直连场景。RegistryDirectory 通过订阅注册中心动态获取 Provider 列表，Provider 上下线时自动刷新。

**Q: 为什么 list() 返回的列表是不可变的？为什么用 volatile？**
A: `unmodifiableList` 防止外部代码意外修改导致并发问题。`volatile` 保证写线程（notify 回调）的修改对读线程（业务调用）立即可见，避免了加锁开销。

## 六、快速复习入口

| 想了解的内容 | 文件路径 |
|-------------|---------|
| 目录抽象基类 | `src/main/java/.../rpc/cluster/directory/AbstractDirectory.java` |
| 静态目录 | `src/main/java/.../rpc/cluster/directory/StaticDirectory.java` |
| 动态目录（重构） | `src/main/java/.../rpc/cluster/directory/RegistryDirectory.java` |
| 多 Provider 协议 | `src/main/java/.../rpc/protocol/DubboProtocol.java` |
| 单元测试 | `src/test/java/.../demo/ApiTest.java` |
