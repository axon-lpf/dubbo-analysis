# Step 07：服务发现与订阅（Directory 动态感知）

## 一、本步骤解决的问题

**核心问题：Consumer 如何实时感知 Provider 的上下线变化？**

Step 06 实现了注册中心，但 Consumer 只在启动时调用 `registry.lookup()` 获取一次 Provider 列表。如果 Provider 在运行期间上线或下线，Consumer 完全不知道。

Step 07 引入 **Directory（服务目录）** 机制，通过订阅-通知模式实现动态服务发现：

```
Step 06（静态发现）:                    Step 07（动态发现）:
═══════════════                       ═══════════════

Consumer 启动时 lookup() 一次          Consumer 创建 Directory → subscribe()
  → 之后不知道 Provider 变化                → 注册中心推送当前列表（第1次）
                                           → Provider 上线 → 推送更新（第N次）
                                           → Provider 下线 → 推送更新（第N次）
                                           → Directory.list() 始终返回最新列表
```

## 二、新增了哪些能力

- ✅ `Directory<T>` — 服务目录接口（Consumer 端的 Provider 本地缓存）
- ✅ `RegistryDirectory<T>` — 基于订阅的动态服务目录实现
- ✅ `InvokerFactory<T>` — URL → Invoker 转换工厂（Directory 内部使用）
- ✅ `DubboProtocol` 升级 — refer() 创建 RegistryDirectory + 管理 Directory 生命周期

## 三、核心类一览

| 类名 | 作用 | Dubbo 对应 |
|------|------|-----------|
| `Directory<T>` | 服务目录接口 | `org.apache.dubbo.rpc.cluster.Directory` |
| `RegistryDirectory<T>` | 订阅注册中心的动态目录 | `org.apache.dubbo.registry.integration.RegistryDirectory` |
| `InvokerFactory<T>` | URL → Invoker 工厂 | —（Dubbo 内部逻辑） |
| `DubboProtocol` (升级) | 创建和管理 Directory | `org.apache.dubbo.rpc.protocol.dubbo.DubboProtocol` |

## 四、核心原理剖析

### 4.1 Directory 的设计思想

Directory 是 Consumer 端的"本地镜像"——它缓存了远程 Provider 的 Invoker 列表，并且通过订阅注册中心保持与真实状态同步。

```
┌─────────────────────────────────────────────────────┐
│                    RegistryDirectory<T>              │
│                                                      │
│  implements Directory<T>, NotifyListener             │
│                                                      │
│  ┌──────────────┐        ┌───────────────────┐      │
│  │ registry     │        │ List<Invoker<T>>   │      │
│  │ .subscribe() │──────→│  invokers           │      │
│  └──────────────┘ notify │  [DubboInvoker@P1, │      │
│                          │   DubboInvoker@P2]  │      │
│                          └────────┬──────────┘      │
│                                   │                  │
│                         list(Invocation)              │
│                         返回最新列表                   │
└─────────────────────────────────────────────────────┘
```

### 4.2 订阅与通知的时序

```
Consumer                Registry              Provider
   │                       │                     │
   │  构造 RegistryDirectory│                     │
   │──subscribe(url, this)→│                     │
   │                       │ 当前无 Provider       │
   │←──── notify([]) ─────│                     │
   │  invokers = []        │                     │
   │                       │                     │
   │                       │←── register(url1) ──│ Provider1 启动
   │                       │                     │
   │←──── notify([url1]) ─│                     │
   │  invokers = [Invoker1]│                     │
   │                       │                     │
   │                       │←── register(url2) ──│ Provider2 启动
   │                       │                     │
   │←── notify([url1,url2])│                     │
   │  invokers = [I1, I2]  │                     │
   │                       │                     │
   │                       │←── unregister(url1) │ Provider1 关闭
   │                       │                     │
   │←──── notify([url2]) ─│                     │
   │  invokers = [I2]      │                     │
```

### 4.3 RegistryDirectory 核心实现

```java
public class RegistryDirectory<T> implements Directory<T>, NotifyListener {

    private volatile List<Invoker<T>> invokers = Collections.emptyList();

    // 构造时立即订阅
    public RegistryDirectory(...) {
        this.registry = registry;
        // 订阅 → 注册中心立即推送当前 Provider 列表
        registry.subscribe(consumerUrl, this);
    }

    // 注册中心回调
    @Override
    public void notify(List<URL> providerUrls) {
        // 1. URL → Invoker（通过 InvokerFactory 创建 DubboInvoker）
        List<Invoker<T>> newInvokers = new ArrayList<>();
        for (URL url : providerUrls) {
            newInvokers.add(invokerFactory.createInvoker(url));
        }
        // 2. 原子替换（volatile write）
        this.invokers = Collections.unmodifiableList(newInvokers);
    }

    // 获取最新列表（O(1) 读，无网络调用）
    @Override
    public List<Invoker<T>> list(Invocation invocation) {
        return invokers;
    }
}
```

### 4.4 RegistryDirectory 中的 List 返回

注意 `list()` 返回的是 `volatile` 变量的引用，读取非常快（O(1)）。
`Collections.unmodifiableList()` 防止外部代码修改内部列表。

```java
// Consumer 调用时：
List<Invoker<T>> invokers = directory.list(invocation);
// 拿到的是最新的、不可变的 Provider Invoker 列表
```

### 4.5 DubboProtocol 的升级

```java
// Step 06: 直接 lookup（静态）
public <T> Invoker<T> refer(Class<T> type, URL url) {
    List<URL> urls = registry.lookup(url);  // 一次性查询
    URL providerUrl = urls.get(0);           // 手动选第一个
    return new DubboInvoker<>(type, providerUrl, codec);
}

// Step 07: 通过 Directory（动态）
public <T> Invoker<T> refer(Class<T> type, URL url) {
    // 创建 RegistryDirectory（自动订阅注册中心）
    RegistryDirectory<T> directory = new RegistryDirectory<>(
        type, url, registry,
        providerUrl -> new DubboInvoker<>(type, providerUrl, codec)
    );

    // Directory.list() 返回实时刷新的 Invoker 列表
    List<Invoker<T>> invokers = directory.list(null);
    return invokers.get(0);
}
```

## 五、面试常见问法

**Q: Dubbo 的 Consumer 如何发现 Provider 的地址？**
A: Consumer 通过 `RegistryDirectory` 订阅注册中心。注册中心在 Provider 上下线时推送变更，Directory 自动将 URL 列表转换为 Invoker 列表并缓存。Consumer 调用时从 Directory 获取最新列表，这个过程对业务代码完全透明。

**Q: RegistryDirectory 和 Registry 有什么区别？**
A: Registry 是注册中心本身（存储 Provider URL），RegistryDirectory 是 Consumer 端对注册中心数据的"本地缓存"。Directory 订阅注册中心，自己维护一份同步的 Invoker 列表，避免每次调用都查注册中心。

**Q: Provider 下线后 Consumer 还能调到吗？**
A: 短期内可能调到（已缓存的 Invoker 还没更新），但下次 Directory 收到注册中心通知后会移除下线的 Invoker。Dubbo 还通过心跳机制检测连接是否有效。

**Q: Directory.list() 为什么用 volatile？**
A: `volatile` 保证多线程环境下的可见性——一个线程（notify 回调线程）修改 `invokers` 后，另一个线程（业务调用线程）能立刻看到最新值，无需加锁。

## 六、快速复习入口

| 想了解的内容 | 文件路径 |
|-------------|---------|
| 服务目录接口 | `src/main/java/.../rpc/cluster/Directory.java` |
| 动态目录实现 | `src/main/java/.../rpc/cluster/directory/RegistryDirectory.java` |
| 集成 Directory 的协议 | `src/main/java/.../rpc/protocol/DubboProtocol.java` |
| 单元测试 | `src/test/java/.../demo/ApiTest.java` |
