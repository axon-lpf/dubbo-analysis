# Step 06：本地服务注册

## 一、本步骤解决的问题

**核心问题：Provider 和 Consumer 如何解耦？**

Step 05 中，Consumer 必须直接知道 Provider 的 IP 和端口才能调用。这在真实场景中不可行——Provider 可能随时上下线、扩缩容、变更地址。注册中心解决了这个问题：

```
Step 05（紧耦合）:                    Step 06（注册中心解耦）:
═══════════════                      ═══════════════════

Consumer ──直接连接──→ Provider       Consumer ──发现──→ Registry ←──注册── Provider
 (必须知道IP:Port)                     (不知道Provider地址)         (不关心谁来调用)
```

## 二、新增了哪些能力

- ✅ `RegistryService` — 注册中心接口（register/unregister/lookup/subscribe）
- ✅ `NotifyListener` — 服务变更通知监听器
- ✅ `AbstractRegistry` — 注册中心抽象基类（订阅管理、变更通知）
- ✅ `LocalRegistry` — 本地内存注册中心实现
- ✅ `DubboProtocol` 升级 — export 自动注册 / refer 自动发现

## 三、核心类一览

| 类名 | 作用 | Dubbo 对应 |
|------|------|-----------|
| `RegistryService` | 注册中心接口 | `org.apache.dubbo.registry.RegistryService` |
| `NotifyListener` | 变更通知监听 | `org.apache.dubbo.registry.NotifyListener` |
| `AbstractRegistry` | 注册中心抽象基类 | `org.apache.dubbo.registry.support.AbstractRegistry` |
| `LocalRegistry` | 本地内存实现 | —（Dubbo 无此实现，架构等价 ZookeeperRegistry） |
| `DubboProtocol` (升级) | 集成注册能力 | `org.apache.dubbo.rpc.protocol.dubbo.DubboProtocol` |

## 四、核心原理剖析

### 4.1 注册中心的核心价值

```
                    ┌──────────────┐
                    │  Registry    │
                    │  (注册中心)   │
                    │              │
                    │ IUserService │
          ┌─────────┤  · 10.0.0.1  ├─────────┐
          │ 注册    │  · 10.0.0.2  │ 发现    │
          │         │  · 10.0.0.3  │         │
     ┌────▼────┐    └──────────────┘    ┌────▼────┐
     │Provider1│                        │Consumer1│
     │10.0.0.1 │                        │         │
     └─────────┘                        │ 只需要知道│
     ┌─────────┐                        │ registry │
     │Provider2│                        │  地址    │
     │10.0.0.2 │                        └─────────┘
     └─────────┘
```

注册中心解决了 3 个关键问题：
1. **位置透明**：Consumer 不需要硬编码 Provider 地址
2. **动态发现**：Provider 上下线时自动更新列表
3. **负载均衡基础**：服务列表为后续集群策略提供数据源

### 4.2 Provider 端：export → register

```java
// DubboProtocol.export() 流程
public <T> Exporter<T> export(Invoker<T> invoker) {
    URL url = invoker.getUrl();

    // 1. 创建 Exporter（本地）
    Exporter<T> exporter = new DubboExporter<>(invoker);
    exporterMap.put(url.getServiceKey(), exporter);

    // 2. 启动端口监听
    startServer(url.getPort());

    // 3. 向注册中心注册（Step 06 新增！）
    registry.register(url);
    //  → AbstractRegistry.register():
    //     registered[serviceKey].add(url)
    //     → notifySubscribers(serviceKey)

    return exporter;
}
```

### 4.3 Consumer 端：lookup → refer

```java
// DubboProtocol.refer() 流程
public <T> Invoker<T> refer(Class<T> type, URL url) {

    // 1. 从注册中心发现 Provider（Step 06 新增！）
    List<URL> providerUrls = registry.lookup(url);
    //  → AbstractRegistry.lookup():
    //      return registered.get(serviceKey)

    // 2. 选择 Provider（当前选第一个，负载均衡在 Step 10）
    URL providerUrl = providerUrls.get(0);

    // 3. 创建远程 Invoker
    return new DubboInvoker<>(type, providerUrl, codec);
}
```

### 4.4 注册中心内部结构

```java
// AbstractRegistry 的数据结构：

// 已注册的服务 URL
ConcurrentMap<String, List<URL>> registered;
// "IUserService:1.0.0" → [
//   dubbo://10.0.0.1:20880/IUserService?version=1.0.0,
//   dubbo://10.0.0.2:20880/IUserService?version=1.0.0
// ]

// 订阅者
ConcurrentMap<String, Set<NotifyListener>> subscribed;
// "IUserService:1.0.0" → [listener1, listener2]
```

### 4.5 订阅与通知机制

```java
// 1. Consumer 订阅
registry.subscribe(conditionUrl, new NotifyListener() {
    @Override
    public void notify(List<URL> urls) {
        // Provider 列表变化时被回调
        System.out.println("Provider 列表已更新: " + urls);
    }
});

// 2. 订阅时立即推送当前列表
// 3. 后续 Provider 上下线时自动通知

// AbstractRegistry.notifySubscribers():
for (NotifyListener listener : listeners) {
    listener.notify(currentUrls);  // 全量推送
}
```

### 4.6 完整调用流程

```
Provider 启动                          Consumer 启动
════════════                         ════════════

1. proxyFactory.getInvoker(impl)     1. protocol.refer(IUserService, url)
   → AbstractProxyInvoker                  │
                                         2. registry.lookup(condition)
2. protocol.export(invoker)                  → registered[serviceKey]
   │                                         → [providerUrl1, providerUrl2]
   ├→ 启动端口监听
   ├→ 创建 DubboExporter                 3. 选第一个 Provider
   └→ registry.register(url)             4. new DubboInvoker(providerUrl)
       → registered[serviceKey].add(url)   5. proxyFactory.getProxy(invoker)
       → notifySubscribers(serviceKey)    6. userService.getUser(1001L)
                                              → 网络调用 → Provider
```

## 五、面试常见问法

**Q: Dubbo 中注册中心的作用是什么？**
A: 注册中心实现 Provider 和 Consumer 的解耦。Provider 启动时向注册中心注册自己的地址，Consumer 启动时从注册中心获取 Provider 地址列表。Provider 上下线时，注册中心通知 Consumer 更新本地地址列表。注册中心是 Dubbo 服务治理的基础设施。

**Q: Dubbo 支持哪些注册中心？**
A: ZooKeeper（推荐）、Nacos、Redis、Consul、Etcd 等。基本原理相同：Provider 创建临时节点（断连自动删除），Consumer Watch 节点变化。本步骤的 LocalRegistry 是内存实现，Step 08 将实现 ZookeeperRegistry。

**Q: 注册中心挂了会影响调用吗？**
A: 不影响。Dubbo Consumer 在启动时从注册中心获取 Provider 列表后会在本地缓存。注册中心不可用时，Consumer 仍可以调用已知的 Provider（通过本地缓存），只是无法感知 Provider 的上下线变化。

**Q: 为什么 AbstractRegistry 要设计订阅通知机制？**
A: Provider 可能随时上线、下线、变更。如果 Consumer 每次调用都去注册中心查询，性能开销大且不及时。订阅通知机制让 Consumer 缓存 Provider 列表，只在变更时更新，兼顾了性能和实时性。

## 六、快速复习入口

| 想了解的内容 | 文件路径 |
|-------------|---------|
| 注册中心接口 | `src/main/java/.../registry/RegistryService.java` |
| 变更通知监听 | `src/main/java/.../registry/NotifyListener.java` |
| 注册中心抽象基类 | `src/main/java/.../registry/support/AbstractRegistry.java` |
| 本地注册中心 | `src/main/java/.../registry/LocalRegistry.java` |
| 集成注册的协议 | `src/main/java/.../rpc/protocol/DubboProtocol.java` |
| 单元测试 | `src/test/java/.../demo/ApiTest.java` |
