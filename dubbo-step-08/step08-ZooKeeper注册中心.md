# Step 08：ZooKeeper 注册中心

## 一、本步骤解决的问题

**核心问题：如何实现生产级的服务注册与发现？**

Step 06 的 LocalRegistry 只能在单 JVM 内工作，Provider 和 Consumer 必须运行在同一进程中。这在实际的分布式系统中完全不可用。

Step 08 引入 **ZooKeeper 注册中心**，利用 ZK 的临时节点实现服务注册，利用 ZK 的 Watch 机制实现服务发现。

```
Step 06（LocalRegistry）:                Step 08（ZookeeperRegistry）:
═══════════════════════                  ══════════════════════════

所有数据在 JVM 内存中                     所有数据在 ZK 节点中
Provider 和 Consumer 必须同 JVM          跨机器、跨进程
Provider 崩溃 → 内存泄漏                  Provider 崩溃 → Session 超时 → 节点自动删除
无持久化能力                               文件缓存容灾
单机                                    ZK 集群保证高可用
```

## 二、新增了哪些能力

- ✅ `ZookeeperRegistry` — 基于 ZK 的注册中心实现
- ✅ 临时节点（EPHEMERAL）— Provider 断连后 ZK 自动清理
- ✅ PathChildrenCache — ZK Watch 机制实时推送变更
- ✅ `AbstractRegistry` 升级 — 暴露监听器管理方法给子类

## 三、核心类一览

| 类名 | 作用 | Dubbo 对应 |
|------|------|-----------|
| `ZookeeperRegistry` | ZK 注册中心实现 | `org.apache.dubbo.registry.zookeeper.ZookeeperRegistry` |
| `AbstractRegistry` (升级) | 新增 protected 监听器管理方法 | `org.apache.dubbo.registry.support.AbstractRegistry` |

## 四、核心原理剖析

### 4.1 ZK 节点结构

```
/dubbo                                        ← 根节点（持久）
  └── com.axon.demo.IUserService:1.0.0       ← 服务节点（持久）
        ├── providers                          ← Provider 目录（持久）
        │     ├── dubbo%3A%2F%2F10.0.0.1...   ← Provider 节点（EPHEMERAL 临时）
        │     └── dubbo%3A%2F%2F10.0.0.2...   ← Provider 节点（EPHEMERAL 临时）
        └── consumers                          ← Consumer 目录（持久）
              └── consumer%3A%2F%2F...
```

**为什么用临时节点？**
1. Provider 正常关闭 → 主动删除节点
2. Provider 异常崩溃 → ZK Session 超时（默认 40 秒）→ ZK 自动删除节点
3. Provider 不需要手动清理注册信息
4. Consumer 通过 Watch 感知节点变化 → 实时更新 Invoker 列表

### 4.2 注册流程（register）

```java
public void register(URL url) {
    // 1. 构建 ZK 路径
    String path = "/dubbo/" + url.getServiceKey()
                + "/providers/" + URLEncoder.encode(url.toString());

    // 2. 递归创建父节点（持久节点）
    client.create().creatingParentsIfNeeded()
          .forPath(parentPath);

    // 3. 创建临时节点
    client.create()
          .withMode(CreateMode.EPHEMERAL)  // ← 关键：临时节点！
          .forPath(path);

    // 4. 通知订阅者
    notifyListeners(serviceKey);
}
```

### 4.3 查找流程（lookup）

```java
public List<URL> lookup(URL condition) {
    String path = "/dubbo/" + condition.getServiceKey() + "/providers";

    // 1. 获取所有子节点名（URL 编码的 Provider URL）
    List<String> children = client.getChildren().forPath(path);

    // 2. 解码 → 重建 URL 对象
    List<URL> urls = new ArrayList<>();
    for (String encoded : children) {
        String urlStr = URLDecoder.decode(encoded, "UTF-8");
        urls.add(rebuildUrl(urlStr));
    }
    return urls;
}
```

### 4.4 订阅流程（subscribe + PathChildrenCache）

```java
public void subscribe(URL url, NotifyListener listener) {
    String path = "/dubbo/" + url.getServiceKey() + "/providers";

    // 1. 创建 PathChildrenCache（ZK Watch 封装）
    PathChildrenCache cache = new PathChildrenCache(client, path, true);
    cache.getListenable().addListener((client, event) -> {
        // 子节点增加/删除/修改 → 重新 lookup → 通知所有监听器
        if (event.getType() in [ADDED, REMOVED, UPDATED]) {
            List<URL> current = lookup(url);
            notifyListeners(serviceKey, current);
        }
    });
    cache.start();

    // 2. 立即推送当前列表
    listener.notify(lookup(url));
}
```

### 4.5 PathChildrenCache 工作原理

```
ZooKeeper                          PathChildrenCache                   NotifyListener
   │                                      │                                   │
   │  Provider 注册临时节点                 │                                   │
   │─── NodeCreated ──────────────────→    │                                   │
   │                                      │─── CHILD_ADDED ───────────────→   │
   │                                      │    notify(currentUrls)            │
   │                                      │                                   │
   │  Provider 断连（Session 超时）          │                                   │
   │─── NodeDeleted ──────────────────→    │                                   │
   │                                      │─── CHILD_REMOVED ─────────────→  │
   │                                      │    notify(currentUrls)            │
```

### 4.6 AbstractRegistry 的升级

```
AbstractRegistry
├── registryUrl          # 注册中心自己的 URL
├── registered           # Map<serviceKey, List<URL>>  (LocalRegistry 使用)
├── subscribed           # Map<serviceKey, Set<NotifyListener>>
│
├── register(url)        # → LocalRegistry 写入 Map
├── unregister(url)      # → LocalRegistry 从 Map 删除
├── lookup(condition)    # → LocalRegistry 从 Map 读取
├── subscribe(...)       # → 添加监听器 + 立即通知
├── unsubscribe(...)     # → 移除监听器
│
└── [新增] protected 方法（Step 08）
    ├── addListener(key, listener)
    ├── removeListener(key, listener)
    ├── hasListeners(key)
    └── notifyListeners(key, urls)   # 显式传入 URL 列表
```

`ZookeeperRegistry` 覆写 `register/unregister/lookup/subscribe/unsubscribe`，
使用 ZK 操作替代 Map 操作，但复用 `subscribed` Map 管理监听器。

## 五、面试常见问法

**Q: Dubbo 为什么推荐 ZooKeeper 作为注册中心？**
A: ZK 的临时节点机制天然适合服务注册——Provider 崩溃后 Session 超时，ZK 自动删除临时节点，Consumer 通过 Watch 实时感知变化。ZK 集群保证高可用，CP 模型保证数据一致性。

**Q: 临时节点（EPHEMERAL）的作用是什么？**
A: 临时节点的生命周期绑定到创建它的 ZK Session。Session 断开（Provider 崩溃/断连）后，ZK 自动删除临时节点。这样注册中心不会残留已宕机的 Provider 信息，Consumer 不会调到已死的服务。

**Q: PathChildrenCache 是什么？**
A: Curator 提供的高级 API，封装了 ZK 的 Watch 机制。它能缓存指定路径下的子节点，并在子节点变化时（增加/删除/修改）回调监听器。Dubbo 用它来感知 Provider 列表变化，而不需要每次手动 getChildren。

**Q: ZK 注册中心和本地缓存的配合是怎样的？**
A: Consumer 启动时从 ZK 拉取 Provider 列表并订阅变更。正常运行时通过 Watch 实时更新本地缓存。如果 ZK 不可用，Consumer 仍能调用已知的 Provider（通过本地缓存容灾）。

## 六、快速复习入口

| 想了解的内容 | 文件路径 |
|-------------|---------|
| ZK 注册中心实现 | `src/main/java/.../registry/zookeeper/ZookeeperRegistry.java` |
| 升级后的抽象基类 | `src/main/java/.../registry/support/AbstractRegistry.java` |
| 单元测试（嵌入式 ZK） | `src/test/java/.../demo/ApiTest.java` |
