# Dubbo RPC 调用全链路流程

> 一次完整的 RPC 调用：从 `userService.getUser(1001L)` 到 `return User{name='User_1001'}`

---

## 一、参与角色

| 角色 | 类 | 位置 | 职责 |
|------|-----|------|------|
| 业务代码 | `Client.main()` | Consumer 端 | 发起调用 |
| 代理层 | `InvokerInvocationHandler` | Consumer 端 | JDK 动态代理拦截 |
| Filter 链 | `MonitorFilter`, `TimeCostFilter` | Consumer 端 | 监控、耗时统计 |
| 集群层 | `FailoverClusterInvoker` | Consumer 端 | 容错 + 负载均衡 |
| 服务目录 | `RegistryDirectory` | Consumer 端 | 从注册中心获取 Provider 列表 |
| 负载均衡 | `RandomLoadBalance` | Consumer 端 | 选择一个 Provider |
| 远程调用器 | `NettyInvoker` | Consumer 端 | 发起网络请求 |
| 编解码器 | `DubboCodec` | 两端 | Dubbo 协议编解码 |
| 传输层 | `NettyServer` / `NettyClient` | 两端 | NIO 网络通信 |
| 服务端分发 | `DubboProtocol.handle()` | Provider 端 | 请求分发 |
| 服务容器 | `DubboExporter` | Provider 端 | 持有 Invoker 引用 |
| Filter 链 | `AccessLogFilter`, `ExceptionFilter`, `MonitorFilter` | Provider 端 | 日志、异常、监控 |
| 反射调用 | `AbstractProxyInvoker` | Provider 端 | 反射调用实现类 |
| 业务实现 | `UserServiceImpl` | Provider 端 | 真正执行业务逻辑 |

---

## 二、泳道图

```
Consumer 端                                   网络                     Provider 端
═══════════                                 ════════                  ═══════════

Client.main()
  │
  │ userService.getUser(1001L)
  ▼
┌─────────────────────────┐
│ InvokerInvocationHandler │ ← JDK 动态代理拦截
│ invoke(proxy, method,   │
│        args)             │
│                          │
│ 1. 构建 RpcInvocation   │
│    接口名: IUserService  │
│    方法名: getUser       │
│    参数: [1001L]         │
└──────────┬──────────────┘
           │ invoker.invoke(invocation)
           ▼
┌─────────────────────────┐
│ MonitorFilter            │ ← Consumer 端监控
│ invoke()                 │   记录开始时间
└──────────┬──────────────┘
           │
           ▼
┌─────────────────────────┐
│ TimeCostFilter           │ ← Consumer 端耗时
│ invoke()                 │   记录开始时间
└──────────┬──────────────┘
           │
           ▼
┌─────────────────────────────────────┐
│ FailoverClusterInvoker              │ ← 集群容错入口
│ doInvoke(invocation)                │
│                                      │
│ 2. directory.list(invocation)        │
│    → [P1, P2, P3]                  │
│                                      │
│ 3. loadBalance.select(invokers)      │
│    → 选 P1(10.0.0.1:20880)         │
│                                      │
│ for i in 0..retries:                 │
│   4. invoker.invoke(invocation) ─────│──┐
│   if fail → 换下一个 Provider        │  │
└──────────────────────────────────────┘  │
                                          │
           ┌──────────────────────────────┘
           ▼
┌─────────────────────────┐
│ NettyInvoker             │ ← 构建网络请求
│ doInvoke(invocation)     │
│                          │
│ 5. Request request =     │
│    new Request(          │
│      id=1,               │
│      svc=IUserService,   │
│      m=getUser,          │
│      args=[1001L]        │
│    )                     │
└──────────┬──────────────┘
           │
           ▼
┌─────────────────────────┐
│ DubboCodec               │ ← 协议编码
│ encode(request)           │
│                          │
│ 6. 写入 Dubbo 协议头     │
│    0xdabb (Magic)        │
│    0x00   (Flag:请求)    │
│    0x00   (Status)       │
│    1      (Req ID)       │
│    430    (Body Length)  │
│    + 序列化 body          │
│ → 446 bytes              │
└──────────┬──────────────┘
           │
           ▼
┌─────────────────────────┐
│ NettyClient              │ ← 发送网络请求
│ channel.writeAndFlush()  │
│                          │
│ 7. pendingRequests       │
│    .put(1, future)       │ ← 注册 Future
│                          │
│ 8. TCP 发送 ──────────── │────────────→  TCP 到达
│    446 bytes             │
└──────────────────────────┘              ┌──────────────────────┐
                                          │ NettyServer          │
                                          │                      │
                                          │ 9. LengthFieldBased   │
                                          │    FrameDecoder       │ ← 解帧
                                          │    读长度 → 读数据    │
                                          │                      │
                                          │ 10. DubboCodec        │
                                          │     decode(bytes)     │ ← 协议解码
                                          │     校验 Magic 0xdabb  │
                                          │     解析 Flag/ReqID   │
                                          │     反序列化 Body     │
                                          │     → Request 对象    │
                                          └──────────┬───────────┘
                                                     │
                                                     ▼
                                          ┌──────────────────────┐
                                          │ DubboProtocol        │
                                          │ handle(request)      │
                                          │                      │
                                          │ 11. 构建 RpcInvocation│
                                          │ 12. findExporter(     │
                                          │     IUserService)     │
                                          │     → exporterMap     │
                                          └──────────┬───────────┘
                                                     │ exporter.getInvoker()
                                                     ▼
                                          ┌──────────────────────┐
                                          │ AccessLogFilter       │ ← Provider 日志
                                          │ 收到调用: IUserService│
                                          │        .getUser       │
                                          │ 参数: [1001]          │
                                          └──────────┬───────────┘
                                                     │
                                                     ▼
                                          ┌──────────────────────┐
                                          │ ExceptionFilter       │ ← Provider 异常
                                          │ try {                 │
                                          │   invoker.invoke()    │
                                          │ } catch {             │
                                          │   包装异常             │
                                          │ }                     │
                                          └──────────┬───────────┘
                                                     │
                                                     ▼
                                          ┌──────────────────────┐
                                          │ MonitorFilter         │ ← Provider 监控
                                          │ 记录耗时              │
                                          └──────────┬───────────┘
                                                     │
                                                     ▼
                                          ┌──────────────────────┐
                                          │AbstractProxyInvoker   │ ← 反射调用
                                          │ doInvoke(invocation)  │
                                          │                      │
                                          │ 13. method =          │
                                          │  proxy.getClass()     │
                                          │  .getMethod("getUser",│
                                          │   Long.class)         │
                                          │                      │
                                          │ 14. result =          │
                                          │  method.invoke(       │
                                          │   proxy, [1001L])     │
                                          └──────────┬───────────┘
                                                     │
                                                     ▼
                                          ┌──────────────────────┐
                                          │ UserServiceImpl       │ ← 业务逻辑
                                          │ getUser(1001L)        │
                                          │                      │
                                          │ 15. return new User(  │
                                          │   1001L, "User_1001", │
                                          │   25, "..."           │
                                          │ )                     │
                                          └──────────┬───────────┘
                                                     │
                                          ← 响应路径  │
           ┌──────────────────────┐                  │
           │ NettyClient          │                  │
           │                      │                  │
           │ 17. pendingRequests   │                  │
           │     .remove(1)        │                  │
           │     future.complete() │                  │
           └──────────┬───────────┘                  │
                      │                              │
                      ▼                              │
           ┌──────────────────────┐                  │
           │ DubboCodec            │                  │
           │ decode(bytes)         │ ← 协议解码       │
           │ → Response            │                  │
           │   success=true        │                  │
           │   result=User{...}    │                  │
           └──────────┬───────────┘                  │
                      │                              │
                      │    ════ TCP 响应 ═════       │
                      │ ◄────────────────────────────┘
                      │           ┌──────────────────────┐
                      │           │ DubboCodec            │
                      │           │ encode(response)      │
                      │           │                      │
                      │           │ 16. 写入 Dubbo 协议头 │
                      │           │    0xdabb (Magic)     │
                      │           │    0x80   (Flag:响应) │
                      │           │    20     (Status:OK) │
                      │           │    1      (Req ID)    │
                      │           │    230    (Body Len)  │
                      │           │    + 序列化 body       │
                      │           │ → 246 bytes           │
                      │           └──────────────────────┘
                      │
           ┌──────────┴───────────┐
           │ Result → 成功         │
           │ result.recreate()     │
           │ → User{name=User_1001}│
           └──────────┬───────────┘
                      │
                      ▼
           ┌──────────────────────┐
           │ TimeCostFilter        │
           │ Consumer 耗时: XXms   │
           └──────────┬───────────┘
                      │
                      ▼
           ┌──────────────────────┐
           │ MonitorFilter         │
           │ 记录: 成功, XXms      │
           │ STATS["getUser"]++    │
           └──────────┬───────────┘
                      │
                      ▼
           ┌──────────────────────┐
           │ InvokerInvocationHandler│
           │ return result         │
           └──────────┬───────────┘
                      │
                      ▼
              Client.main()
              User user = userService.getUser(1001L)
              // user = User{id=1001, name='User_1001'}
              // ↑ 看起来像本地调用，实际经过了 15+ 个环节！
```

---

## 三、分步详解

### 第 1 步：业务代码发起调用

```java
// Client.main() - Consumer 端
IUserService userService = referenceConfig.get();
User user = userService.getUser(1001L);
```

`userService` 不是真正的 `UserServiceImpl`，而是一个 **JDK 动态代理对象**。
调用 `getUser(1001L)` 时，JVM 将其路由到 `InvokerInvocationHandler.invoke()`。

### 第 2-4 步：Consumer Filter 链 + 集群容错

```java
// InvokerInvocationHandler.invoke()
// 1. 构建 RpcInvocation
RpcInvocation inv = new RpcInvocation("IUserService", "getUser",
        new String[]{"java.lang.Long"}, new Object[]{1001L});

// 2. 经过 Consumer Filter 链
//   MonitorFilter.invoke() → TimeCostFilter.invoke()
//     → FailoverClusterInvoker.doInvoke()

// 3. FailoverClusterInvoker 内部:
//   a) directory.list() → 从注册中心获取 [P1, P2, P3]
//   b) loadBalance.select() → 选 P1 (10.0.0.1:20880)
//   c) invoker.invoke(invocation) → 调用 P1
//   d) 失败 → 换 P2 重试 (最多 retries 次)
```

### 第 5-6 步：构建网络请求 + 协议编码

```java
// NettyInvoker.doInvoke()
// 5. 构建 Request
Request request = new Request(1L, "IUserService", "getUser",
        new String[]{"java.lang.Long"}, new Object[]{1001L});

// 6. DubboCodec.encode(request)
// → 16 字节协议头 + 序列化 body
// ┌────────┬──────┬────────┬──────────┬──────────┬──────────┐
// │ 0xdabb │ 0x00 │  0x00  │    1     │   430    │ 序列化   │
// │ Magic  │Flag  │ Status │ Req ID   │ Body Len │ Request  │
// │ 2B     │ 1B   │  1B    │   8B     │   4B     │  N bytes │
// └────────┴──────┴────────┴──────────┴──────────┴──────────┘
```

### 第 7-8 步：Netty 发送 + 异步等待

```java
// 7. 注册 Future (请求 ID → CompletableFuture)
pendingRequests.put(1L, future);

// 8. 异步发送（Netty Channel.writeAndFlush）
channel.writeAndFlush(request);
// 当前线程阻塞在 future.get() 等待响应
```

### 第 9-12 步：Netty 接收 + 服务定位

```java
// 9. LengthFieldBasedFrameDecoder 解帧
//    读 4 字节长度 → 430 → 读 430 字节 → 完整帧

// 10. DubboCodec.decode(bytes)
//    校验 Magic(0xdabb) → 提取 Req ID(1) → 反序列化 body → Request

// 11-12. DubboProtocol.handle()
//    构建 RpcInvocation → findExporter("IUserService")
//    → 从 exporterMap 获取 Exporter → getInvoker()
```

### 第 13-15 步：反射调用 + 业务执行

```java
// 13-14. AbstractProxyInvoker.doInvoke()
Method method = UserServiceImpl.class.getMethod("getUser", Long.class);
Object result = method.invoke(userServiceImpl, 1001L);

// 15. UserServiceImpl.getUser(1001L)
return new User(1001L, "User_1001", 25, "user1001@example.com");
```

### 第 16 步：响应编码返回

```java
// 16. DubboCodec.encode(response)
//    如果成功:
// ┌────────┬──────┬────────┬──────────┬──────────┬──────────┐
// │ 0xdabb │ 0x80 │   20   │    1     │   230    │ 序列化   │
// │ Magic  │Flag  │Status  │ Req ID   │ Body Len │ Response │
// │        │ 响应 │  OK    │          │          │          │
// └────────┴──────┴────────┴──────────┴──────────┴──────────┘
```

### 第 17 步：Consumer 端唤醒

```java
// 17. NettyClientHandler.channelRead0()
//    pendingRequests.remove(1) → future.complete(response)
//    → send() 中的 future.get() 解除阻塞 → 返回 Response
```

---

## 四、关键数据结构流转

```
Consumer 端                           Provider 端
═══════════                           ═══════════


RpcInvocation                         RpcInvocation
{                                     {
  svc: "IUserService",                  svc: "IUserService",
  method: "getUser",                    method: "getUser",
  types: ["long"],                      types: ["long"],
  args: [1001L]                         args: [1001L]
}                                     }
    │                                     ▲
    ▼                                     │
Request                                Request (解码)
{                                     {
  id: 1,                                id: 1,
  svc: "IUserService",                  svc: "IUserService",
  method: "getUser",                    method: "getUser",
  types: ["long"],                      types: ["long"],
  args: [1001L]                         args: [1001L]
}                                     }
    │                                     │
    │  DubboCodec.encode()                │  DubboCodec.decode()
    ▼                                     │
[0xdabb][0x00][0x00][1][430][...]   ←── 网络传输 ──→  [0xdabb][0x00][0x00][1][430][...]
                                                    │
                                            DubboCodec.encode()
                                                    │
                                            [0xdabb][0x80][20][1][230][...]
                                                    │
                                            ←── 网络传输 ──→
    │
    │  DubboCodec.decode()
    ▼
Response                                Response (编码)
{                                       {
  id: 1,                                  id: 1,
  success: true,                          success: true,
  result: User{                           result: User{
    id: 1001,                               id: 1001,
    name: "User_1001",                      name: "User_1001",
    age: 25,                                age: 25,
    email: "..."                            email: "..."
  }                                       }
}                                       }
    │
    ▼
RpcResult → User{name='User_1001'}
```

---

## 五、一次调用经过的完整 Filter 链

```
Consumer 端 Filter 链 (调用前)
═══════════════════════════
  MonitorFilter.invoke()     → 记录请求开始时间
    TimeCostFilter.invoke()  → 记录请求开始时间
      FailoverClusterInvoker.doInvoke()
        → 选择 Provider → 网络调用 → 等待响应
      TimeCostFilter         → 打印: "Consumer 耗时: XXms"
    MonitorFilter            → STATS["IUserService.getUser"].record(成功, XXms)


Provider 端 Filter 链 (收到请求后)
═══════════════════════════
  AccessLogFilter.invoke()   → 打印: "[AccessLog] 收到调用: IUserService.getUser"
    ExceptionFilter.invoke() → try { invoker.invoke() } catch { 包装异常 }
      MonitorFilter.invoke() → 记录开始时间
        AbstractProxyInvoker.doInvoke()
          → 反射调用 UserServiceImpl.getUser(1001L)
        MonitorFilter        → STATS["IUserService.getUser"].record(成功, XXms)
      ExceptionFilter        → 如果有异常，包装为 RpcResult
    AccessLogFilter          → 打印: "[AccessLog] 调用完成: 耗时 XXms"
```

---

## 六、异常情况下的调用路径

```
正常情况:
  getUser(1001L)
    → FailoverClusterInvoker
      → select(P1) → invoke → 成功 ✓
      → 返回 User{...}

P1 失败情况 (Failover, retries=2):
  getUser(1001L)
    → FailoverClusterInvoker
      → select(P1) → invoke → Connection refused ✗
        → 记录 P1 已失败
      → select(P2) → invoke → Connection refused ✗
        → 记录 P2 已失败
      → select(P3) → invoke → 成功 ✓
      → 返回 User{...}

全部失败情况:
  getUser(1001L)
    → FailoverClusterInvoker
      → 第 1 次: P1 ✗ → 第 2 次: P2 ✗ → 第 3 次: P3 ✗
      → throw RuntimeException("Failover: 全部 3 次尝试失败")
      → ExceptionFilter 包装
      → Consumer 收到异常
```

---

## 七、类与官方源码对应

| 我们的类 | 官方 Dubbo 类 | 所属层 |
|---------|-------------|--------|
| `InvokerInvocationHandler` | `org.apache.dubbo.rpc.proxy.InvokerInvocationHandler` | Proxy |
| `MonitorFilter` | `org.apache.dubbo.monitor.support.MonitorFilter` | Filter |
| `FailoverClusterInvoker` | `org.apache.dubbo.rpc.cluster.support.FailoverClusterInvoker` | Cluster |
| `RegistryDirectory` | `org.apache.dubbo.registry.integration.RegistryDirectory` | Directory |
| `RandomLoadBalance` | `org.apache.dubbo.rpc.cluster.loadbalance.RandomLoadBalance` | LoadBalance |
| `NettyInvoker` | `org.apache.dubbo.rpc.protocol.dubbo.DubboInvoker` | Protocol |
| `DubboCodec` | `org.apache.dubbo.remoting.exchange.codec.ExchangeCodec` | Codec |
| `NettyServer` | `org.apache.dubbo.remoting.transport.netty4.NettyServer` | Transport |
| `AbstractProxyInvoker` | `org.apache.dubbo.rpc.proxy.AbstractProxyInvoker` | Proxy |
| `UserServiceImpl` | 用户的业务实现 | Business |
