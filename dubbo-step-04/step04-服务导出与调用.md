# Step 04：服务导出与 Invoker/Exporter 体系

## 一、本步骤解决的问题

**核心问题：服务端如何以规范化的方式暴露服务、分发请求、执行调用？**

Step 03 用 `Map<String, Object>` + 手动反射实现了基本功能，但这只是临时方案。Step 04 将其升级为 Dubbo 的核心抽象体系：

```
Step 03（临时）:  Map<String, Object> → 手动 getMethod + invoke
Step 04（正式）:  Invoker → Exporter → URL → 标准化的服务导出与调用
```

## 二、新增了哪些能力

- ✅ `URL` — Dubbo 核心数据模型（配置总线）
- ✅ `Invoker<T>` — 调用器接口（Provider 和 Consumer 的统一抽象）
- ✅ `Invocation` — RPC 调用元数据接口
- ✅ `Result` / `RpcResult` — 调用结果封装
- ✅ `Exporter<T>` — 服务导出器（生命周期管理）
- ✅ `AbstractInvoker<T>` — 模板方法基类
- ✅ `AbstractProxyInvoker<T>` — Provider 端反射执行器
- ✅ `DubboExporter<T>` — 导出器实现
- ✅ `ExporterServer` — 基于 Invoker/Exporter 体系的服务端

## 三、核心类一览

| 类名 | 作用 | Dubbo 对应 |
|------|------|-----------|
| `URL` | 配置总线，贯穿所有组件 | `org.apache.dubbo.common.URL` |
| `Invoker<T>` | 调用器接口（最核心抽象） | `org.apache.dubbo.rpc.Invoker` |
| `Invocation` | 调用元数据接口 | `org.apache.dubbo.rpc.Invocation` |
| `Result` / `RpcResult` | 调用结果封装 | `org.apache.dubbo.rpc.Result` |
| `Exporter<T>` | 服务导出器 | `org.apache.dubbo.rpc.Exporter` |
| `AbstractInvoker<T>` | Invoker 模板方法基类 | `org.apache.dubbo.rpc.support.AbstractInvoker` |
| `AbstractProxyInvoker<T>` | Provider 端反射 Invoker | `org.apache.dubbo.rpc.proxy.AbstractProxyInvoker` |
| `DubboExporter<T>` | Dubbo 协议导出器 | `org.apache.dubbo.rpc.protocol.AbstractExporter` |

## 四、核心原理剖析

### 4.1 URL — Dubbo 的"配置总线"

```
URL 格式: protocol://host:port/path?key1=value1&key2=value2

示例:
dubbo://192.168.1.100:20880/com.axon.demo.IUserService?version=1.0.0&timeout=3000
```

URL 在 Dubbo 中的作用不是"网址"，而是"配置的标准化载体"：

```java
// 注册中心配置
registry://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?registry=zookeeper

// 服务提供者
dubbo://192.168.1.100:20880/com.axon.demo.IUserService?version=1.0.0

// 服务消费者
consumer://192.168.1.200/com.axon.demo.IUserService?application=app1

// 监控中心
monitor://192.168.1.50:7070/com.axon.dubbo.monitor.MonitorService
```

> 💡 理解 URL 的设计意图：所有 Dubbo 组件通过 URL 获取配置信息，实现"配置驱动"而非"代码耦合"。

### 4.2 Invoker — 最核心的抽象

```
                    ┌──────────────────┐
                    │   Invoker<T>      │
                    │                   │
                    │ getInterface()    │  ← 服务接口类型
                    │ invoke(Invocation)│  ← 执行调用
                    │ getUrl()          │  ← 服务 URL
                    │ isAvailable()     │  ← 是否可用
                    │ destroy()         │  ← 销毁
                    └──────┬───────────┘
                           │
            ┌──────────────┴──────────────┐
            │                             │
    Provider 端                      Consumer 端
    ┌────────────────┐          ┌────────────────┐
    │AbstractProxyInvoker│      │ (未来：DubboInvoker)│
    │                         │  │                         │
    │ doInvoke():             │  │ doInvoke():             │
    │   method.invoke(proxy,  │  │   网络发送 → Provider   │
    │   args)                 │  │   接收 Result           │
    └─────────────────────────┘  └─────────────────────────┘
```

**Invoker 的统一性**：Provider 和 Consumer 使用同一个接口，这使得：
- Filter 链可以同时用于两端
- 可以在 Invoker 上做各种装饰（缓存、限流、监控）
- 测试时可以直接 mock Invoker

### 4.3 Exporter — 服务导出的生命周期

```java
// 服务导出流程
URL url = URL.builder().protocol("dubbo").host("127.0.0.1").port(20880)
        .path(IUserService.class.getName()).build();

// 1. 将实现类包装为 Invoker
Invoker<IUserService> invoker = proxyFactory.getInvoker(
        new UserServiceImpl(), IUserService.class, url);

// 2. 导出 Invoker → 创建 Exporter
Exporter<IUserService> exporter = new DubboExporter<>(invoker);
// 此时服务已对外暴露

// 3. 关闭时取消导出
exporter.unexport();  // → invoker.destroy()
```

### 4.4 ExporterServer 的请求分发流程

```
客户端请求到达
      │
      ▼
┌─────────────────────────────────────────────┐
│ ExporterServer.handleClient()               │
│                                              │
│ 1. 反序列化 Request                          │
│    └→ Request{interface: IUserService, ...}  │
│                                              │
│ 2. handle(Request)                           │
│    ├→ 构建 RpcInvocation                     │
│    ├→ findExporter(interfaceName)            │
│    │   └→ 从 exporterMap 查找匹配的 Exporter │
│    ├→ exporter.getInvoker()                  │
│    ├→ invoker.invoke(invocation)             │
│    │   └→ AbstractInvoker.invoke()           │
│    │       └→ AbstractProxyInvoker.doInvoke()│
│    │           └→ method.invoke(proxy, args) │
│    └→ Result → Response                      │
│                                              │
│ 3. 序列化并发送 Response                      │
└─────────────────────────────────────────────┘
```

### 4.5 AbstractInvoker 的模板方法模式

```java
// 模板方法：定义算法骨架
public Result invoke(Invocation invocation) {
    if (!available) {
        return new RpcResult(new IllegalStateException("不可用"));
    }
    try {
        return doInvoke(invocation);  // ← 子类实现
    } catch (Throwable e) {
        return new RpcResult(e);       // 统一异常包装
    }
}

// Provider 端的 doInvoke：
protected Result doInvoke(Invocation inv) throws Throwable {
    Method method = proxy.getClass().getMethod(inv.getMethodName(), paramTypes);
    Object result = method.invoke(proxy, inv.getArguments());
    return new RpcResult(result);
}

// Consumer 端的 doInvoke（未来的 DubboInvoker）：
protected Result doInvoke(Invocation inv) throws Throwable {
    // 发送网络请求...
    // 接收响应...
    // 返回 Result
}
```

### 4.6 当前架构全景

```
┌──────────────────────────────────────────────────────────────────┐
│                         Dubbo 核心抽象（Step 04）                  │
├──────────────────────────────────────────────────────────────────┤
│                                                                    │
│  Consumer 端                         Provider 端                   │
│  ═══════════                         ═══════════                   │
│                                                                    │
│  IUserService proxy                    ExporterServer              │
│       │                                      │                     │
│       │ 调用接口方法                           │ 启动服务             │
│       ▼                                      ▼                     │
│  InvokerInvocationHandler              export(Class, impl)         │
│       │                                      │                     │
│       │ 构建 RpcInvocation                    ▼                     │
│       │ 构建 Request                  ┌──────────────┐             │
│       ▼                              │ JdkProxyFactory│            │
│  ObjectClient.send()                 │ .getInvoker() │             │
│       │                              └──────┬───────┘             │
│       │ TCP 发送                            │                      │
│       │                              ┌──────▼──────────┐          │
│       │                              │AbstractProxyInvoker│       │
│       │                              │ (doInvoke: 反射)  │        │
│       │                              └──────┬──────────┘          │
│       │                                     │                      │
│       │                              ┌──────▼──────┐              │
│       │                              │DubboExporter │              │
│       │                              │ exporterMap  │              │
│       │                              └──────┬──────┘              │
│       │                                     │                      │
│       │   ◄════════ 网络传输 ════════►       │                      │
│       │                                     ▼                      │
│  Response ◄────────────────────── ExporterServer.handle()         │
│       │                              │                             │
│       ▼                              ├ findExporter()              │
│  返回 result                         ├ invoker.invoke(invocation)  │
│                                      └ Response                    │
│                                                                    │
└──────────────────────────────────────────────────────────────────┘
```

## 五、面试常见问法

**Q: Dubbo 中的 Invoker 是什么？为什么说它是核心抽象？**
A: Invoker 是 Dubbo 中"可执行调用"的抽象。Provider 端的 Invoker 通过反射执行本地方法，Consumer 端的 Invoker 通过网络发送请求。两者统一使用 Invoker 接口，使得 Filter、Listener 等机制可以同时作用于两端。

**Q: URL 在 Dubbo 中是什么作用？**
A: URL 是 Dubbo 的"配置总线"。服务元数据、注册中心地址、协议参数等所有配置信息都以 URL 形式表示和传递。设计上实现了"配置驱动"——组件之间通过 URL 交换信息，而不是直接耦合。

**Q: Exporter 和 Invoker 的关系是什么？**
A: Protocol.export(invoker) → Exporter。Exporter 是导出操作的结果，持有被导出的 Invoker 引用，管理服务的"暴露"和"取消暴露"生命周期。

**Q: Dubbo 的 Provider 端如何执行一个请求？**
A: 请求到达 → 反序列化 Request → 构建 RpcInvocation → 查找 Exporter → 获取 Invoker → invoke(invocation) → AbstractInvoker 模板方法 → AbstractProxyInvoker.doInvoke() → getMethod + method.invoke → 返回 RpcResult → 封装 Response → 序列化返回。

## 六、快速复习入口

| 想了解的内容 | 文件路径 |
|-------------|---------|
| URL 配置总线 | `src/main/java/.../common/URL.java` |
| Invoker 接口 | `src/main/java/.../rpc/Invoker.java` |
| Invocation 接口 | `src/main/java/.../rpc/Invocation.java` |
| Result 接口 | `src/main/java/.../rpc/Result.java` / `RpcResult.java` |
| Exporter 接口 | `src/main/java/.../rpc/Exporter.java` |
| AbstractInvoker（模板方法） | `src/main/java/.../rpc/support/AbstractInvoker.java` |
| AbstractProxyInvoker（反射） | `src/main/java/.../rpc/proxy/AbstractProxyInvoker.java` |
| DubboExporter | `src/main/java/.../rpc/protocol/DubboExporter.java` |
| ExporterServer（集成） | `src/main/java/.../transport/socket/ExporterServer.java` |
| 单元测试 | `src/test/java/.../demo/ApiTest.java` |
