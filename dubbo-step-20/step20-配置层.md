# Step 20：配置层（ServiceConfig / ReferenceConfig）

## 一、本步骤解决的问题

**核心问题：Provider/Consumer 启动流程涉及太多底层细节，如何简化？**

Step 19 中，启动一个 Provider 需要手动创建 Registry、Protocol、ProxyFactory、URL、Invoker、Exporter——至少 10 行代码。Step 20 引入声明式配置层，将这些细节封装在 `ServiceConfig` 和 `ReferenceConfig` 中。

```
Step 19（手动流程，10+ 行）:              Step 20（声明式，4 行）:
══════════════════════════               ═════════════════════

RegistryService registry = ...           ServiceConfig<T> svc = new ServiceConfig<>();
Protocol protocol = ...                  svc.setInterface(IXxx.class);
ProxyFactory proxyFactory = ...          svc.setRef(new XxxImpl());
URL url = URL.builder()...               svc.export();  // 一键启动！
Invoker<T> inv = ...
Exporter<T> exp = protocol.export(inv);
```

## 二、新增了哪些能力

- ✅ `RegistryConfig` — 注册中心配置
- ✅ `ProtocolConfig` — 协议配置
- ✅ `ServiceConfig<T>` — Provider 配置（一键 export）
- ✅ `ReferenceConfig<T>` — Consumer 配置（一行 get）

## 三、核心类一览

| 类名 | 作用 | Dubbo 对应 |
|------|------|-----------|
| `ServiceConfig<T>` | Provider 端服务配置 | `org.apache.dubbo.config.ServiceConfig` |
| `ReferenceConfig<T>` | Consumer 端引用配置 | `org.apache.dubbo.config.ReferenceConfig` |
| `RegistryConfig` | 注册中心地址配置 | `org.apache.dubbo.config.RegistryConfig` |
| `ProtocolConfig` | 协议端口配置 | `org.apache.dubbo.config.ProtocolConfig` |

## 四、核心原理剖析

### 4.1 ServiceConfig.export() 内部流程

```java
public void export() {
    // 1. 创建注册中心
    registryService = new LocalRegistry(registry.getAddress());

    // 2. 创建协议
    dubboProtocol = new DubboProtocol(registryService);

    // 3. 构建 URL（从配置自动生成）
    URL serviceUrl = URL.builder()
        .protocol("dubbo").host(protocol.getHost()).port(protocol.getPort())
        .path(interfaceClass.getName()).addParameter("version", version)
        .build();

    // 4. 创建 Invoker + export
    Invoker<T> invoker = proxyFactory.getInvoker(ref, interfaceClass, serviceUrl);
    exporter = dubboProtocol.export(invoker);
}
```

用户不需要知道 Registry、Protocol、ProxyFactory、URL、Invoker、Exporter 这些内部类。
只需要：设置接口、设置实现类、调用 export()。

### 4.2 ReferenceConfig.get() 内部流程

```java
public T get() {
    // 1. 创建注册中心 + 协议
    // 2. 构建消费 URL
    // 3. refer → Invoker → Proxy
    // 4. 缓存 Proxy

    ProxyFactory proxyFactory = new JdkProxyFactory();
    Invoker<T> invoker = dubboProtocol.refer(interfaceClass, url);
    proxy = proxyFactory.getProxy(invoker);
    return proxy;
}
```

首次调用 `get()` 执行完整的 refer 流程，后续调用直接返回缓存的 proxy。

### 4.3 配置优先级（Dubbo 设计思想）

```
方法级 > 接口级 > Provider/Consumer 级 > 全局级
-D JVM 参数 > XML > properties > 默认值
```

我们的实现简化了优先级链，但保留了核心设计思想：用户通过 Config 对象声明意图，框架负责执行。

### 4.4 从手动到声明式的演进

```
Step 05: 手动构造 Request, 手动调用 client.send()
Step 14: 通过 Proxy + Protocol 透明化
Step 19: 但仍需手动创建 Registry, Protocol, ProxyFactory, URL
Step 20: 声明式配置，四行代码完成全部启动！
```

## 五、面试常见问法

**Q: Dubbo 的 ServiceConfig 和 ReferenceConfig 做了什么？**
A: ServiceConfig 封装了 Provider 端服务导出的完整流程（注册中心连接、协议启动、URL 构建、Invoker 创建、export）。ReferenceConfig 封装了 Consumer 端服务引用的完整流程（注册中心连接、服务发现、Invoker 创建、代理生成）。

**Q: Dubbo 的配置优先级是怎样的？**
A: 方法级 > 接口级 > Provider/Consumer 级 > 全局级。JVM -D 参数 > XML > properties 文件 > 默认值。Dubbo 支持 in-JVM、外部文件、注册中心、配置中心等多种配置来源，并有完整的覆盖规则。

## 六、快速复习入口

| 想了解的内容 | 文件路径 |
|-------------|---------|
| ServiceConfig | `src/main/java/.../config/ServiceConfig.java` |
| ReferenceConfig | `src/main/java/.../config/ReferenceConfig.java` |
| RegistryConfig | `src/main/java/.../config/RegistryConfig.java` |
| ProtocolConfig | `src/main/java/.../config/ProtocolConfig.java` |
| 单元测试 | `src/test/java/.../demo/ApiTest.java` |
