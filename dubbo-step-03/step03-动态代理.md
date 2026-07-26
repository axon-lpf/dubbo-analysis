# Step 03：动态代理（客户端 Stub）

## 一、本步骤解决的问题

**核心问题：如何让远程调用像本地调用一样透明？**

Step 02 中，客户端需要手动构造 `Request` 对象、设置接口名、方法名、参数类型、参数值，然后调用 `client.send(request)` 获取响应。这离 "透明化 RPC 调用" 还很远。

本步骤引入 **JDK 动态代理**，让调用方只需要拿到接口对象，像调用普通方法一样调用，底层的网络通信、序列化、协议等细节全部被代理层封装。

```java
// Step 02：手动构造请求（侵入性强、重复代码多）
Request request = new Request(1L, "com.axon.UserService", "getUser",
        new String[]{"java.lang.Long"}, new Object[]{1001L});
Response response = client.send(request);

// Step 03：透明代理（就像调用本地方法！）
IUserService userService = proxyFactory.createProxy(IUserService.class, client);
User user = userService.getUser(1001L);   // ← 完全透明！
```

## 二、新增了哪些能力

- ✅ `ProxyFactory` 代理工厂接口
- ✅ `JdkProxyFactory` JDK 动态代理实现
- ✅ `InvokerInvocationHandler` 代理调用拦截器
- ✅ `RpcInvocation` RPC 调用元数据封装
- ✅ `ObjectServer` 增强：内置服务注册表 + 反射调用
- ✅ 支持 `toString()/hashCode()/equals()` 等 Object 方法本地处理

## 三、核心类一览

| 类名 | 作用 | Dubbo 中对应概念 |
|------|------|-----------------|
| `ProxyFactory` | 代理工厂接口 | `org.apache.dubbo.rpc.ProxyFactory`（@SPI） |
| `JdkProxyFactory` | JDK 动态代理实现 | `org.apache.dubbo.rpc.proxy.jdk.JdkProxyFactory` |
| `InvokerInvocationHandler` | 方法调用拦截器（核心） | `org.apache.dubbo.rpc.proxy.InvokerInvocationHandler` |
| `RpcInvocation` | RPC 调用元数据封装 | `org.apache.dubbo.rpc.RpcInvocation` |

## 四、核心原理剖析

### 4.1 代理模式在 RPC 中的应用

```
客户端代码                       代理层                      网络层
══════════                     ════════                    ════════

userService.getUser(1001L)     InvokerInvocationHandler     ObjectClient
       │                              │                        │
       │  看起来是本地调用              │                        │
       │─────────────────────→        │                        │
       │                              │ 1. 拦截方法调用         │
       │                              │ 2. 提取方法元数据       │
       │                              │ 3. 构造 Request         │
       │                              │─────────────────────→  │
       │                              │                        │──→ 网络
       │                              │                        │←── 网络
       │                              │ 5. 从 Response 提取结果 │
       │                              │←─────────────────────  │
       │  返回 User 对象               │                        │
       │←─────────────────────        │                        │
       │                              │                        │
```

### 4.2 InvokerInvocationHandler 执行流程

```java
public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    // ═══ 第 1 步：过滤 Object 方法 ═══
    // toString()、hashCode()、equals() 直接本地执行，不需要远程调用
    if (method.getDeclaringClass() == Object.class) {
        return method.invoke(this, args);
    }

    // ═══ 第 2 步：提取调用信息 ═══
    String interfaceName = interfaceClass.getName();     // "com.axon.demo.IUserService"
    String methodName = method.getName();                // "getUser"
    String[] paramTypes = resolveParamTypes(method);     // ["java.lang.Long"]
    Object[] arguments = args;                           // [1001L]

    // ═══ 第 3 步：构造网络请求 ═══
    Request request = new Request(
        REQUEST_ID.incrementAndGet(),
        interfaceName, methodName, paramTypes, arguments
    );

    // ═══ 第 4 步：发送请求 ═══
    Response response = client.send(request);

    // ═══ 第 5 步：提取结果 ═══
    if (response.isSuccess()) {
        return response.getResult();      // 返回给调用方
    } else {
        throw new RuntimeException(response.getErrorMessage());
    }
}
```

### 4.3 JDK 动态代理原理（精简版）

```java
// java.lang.reflect.Proxy 的核心逻辑（简化）：

// 1. Proxy.newProxyInstance() 内部调用 getProxyClass0() 获取/生成代理类
//    生成的代理类大致长这样：
public final class $Proxy0 extends Proxy implements IUserService {
    private static Method m3;  // getUser 方法

    static {
        m3 = Class.forName("com.axon.demo.IUserService").getMethod("getUser", Long.class);
    }

    public $Proxy0(InvocationHandler h) { super(h); }

    @Override
    public User getUser(Long id) {
        // 所有方法调用都被委托给 InvocationHandler
        return (User) h.invoke(this, m3, new Object[]{id});
    }
}

// 2. 实际调用链路：
userService.getUser(1001L)
    → $Proxy0.getUser(1001L)
        → InvocationHandler.invoke(proxy, method, args)
            → 构造 Request → Socket 发送 → 解析 Response → 返回结果
```

### 4.4 服务端改造：服务注册 + 反射调用

Step 03 的 `ObjectServer` 相比 Step 02 新增了关键能力：

```java
// 服务注册表：接口名 → 实现类实例
private final Map<String, Object> serviceMap = new ConcurrentHashMap<>();

// 注册服务
public <T> void registerService(Class<T> interfaceClass, T implementation) {
    serviceMap.put(interfaceClass.getName(), implementation);
}

// 收到请求后的处理流程：
// 1. 通过 interfaceName 查找服务实现
// 2. 通过 methodName + parameterTypes 找到对应 Method
// 3. 反射调用 method.invoke(serviceImpl, arguments)
// 4. 将结果封装为 Response 返回
```

### 4.5 调用时序图

```
Client.main()         Proxy          InvocationHandler    ObjectClient    ObjectServer    UserServiceImpl
     │                  │                    │                │               │                │
     │ createProxy()    │                    │                │               │                │
     │─────────────────→│                    │                │               │                │
     │                  │  new Proxy(h)      │                │               │                │
     │                  │───────────────────→│                │               │                │
     │ getUser(1001L)   │                    │                │               │                │
     │─────────────────→│                    │                │               │                │
     │                  │  invoke()          │                │               │                │
     │                  │───────────────────→│                │               │                │
     │                  │                    │ send(request)  │               │                │
     │                  │                    │───────────────→│               │                │
     │                  │                    │                │  TCP 发送     │                │
     │                  │                    │                │──────────────→│                │
     │                  │                    │                │               │ getUser(1001L) │
     │                  │                    │                │               │───────────────→│
     │                  │                    │                │               │   User obj    │
     │                  │                    │                │               │←───────────────│
     │                  │                    │                │  TCP 响应     │                │
     │                  │                    │                │←──────────────│                │
     │                  │                    │  Response      │               │                │
     │                  │                    │←───────────────│               │                │
     │                  │  返回 User         │                │               │                │
     │                  │←───────────────────│                │               │                │
     │  User 对象         │                    │                │               │                │
     │←─────────────────│                    │                │               │                │
```

## 五、面试常见问法

**Q: Dubbo 中客户端怎么调用远程服务的？**
A: Dubbo 使用动态代理。客户端通过 `ReferenceConfig.get()` 获得的是一个代理对象（默认用 Javassist 生成），当调用接口方法时，实际进入 `InvokerInvocationHandler.invoke()`，在其中完成：方法元数据提取 → 构造 RPC Invocation → 通过 Cluster → LoadBalance → Protocol → Transport 发送到服务端 → 拿到结果返回。

**Q: JDK 动态代理和 CGLIB/Javassist 代理有什么区别？为什么 Dubbo 默认用 Javassist？**
A: JDK 代理只能代理接口，CGLIB/Javassist 可以代理类和接口。Dubbo 选 Javassist 是因为：
1. 可以代理类（比如泛化调用场景）
2. 不需要接口也能工作
3. 生成的字节码性能更好
4. 避免了 JDK 代理的反射开销

**Q: 代理中如何处理 Object 的方法（toString/equals/hashCode）？**
A: 通过 `method.getDeclaringClass() == Object.class` 判断，如果是 Object 的方法，直接在本地执行，不走远程调用。

**Q: 一个代理对象调用多个方法时，如何区分不同的请求？**
A: 每个请求都有唯一的 Request ID（通过 AtomicLong 自增），服务端在 Response 中带回相同的 ID，客户端通过 ID 匹配请求和响应。

## 六、快速复习入口

| 想了解的内容 | 文件路径 |
|-------------|---------|
| 代理工厂接口 | `src/main/java/.../rpc/proxy/ProxyFactory.java` |
| JDK 代理实现 | `src/main/java/.../rpc/proxy/jdk/JdkProxyFactory.java` |
| 调用拦截器（核心） | `src/main/java/.../rpc/proxy/jdk/InvokerInvocationHandler.java` |
| RPC 调用封装 | `src/main/java/.../rpc/RpcInvocation.java` |
| 服务端（增强） | `src/main/java/.../transport/socket/ObjectServer.java` |
| 测试用例 | `src/test/java/.../demo/ApiTest.java` |
