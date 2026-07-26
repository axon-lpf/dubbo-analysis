# Dubbo RPC 调用链路 —— 面试回答指南

---

## 一、总览版（30秒，说清骨架）

> 面试官：说说 Dubbo 的一次 RPC 调用过程。

```
一句话：Consumer 代理拦截 → Filter 链 → 集群容错 → 协议编码 → Netty 发送
         → Provider Netty 接收 → 协议解码 → Filter 链 → 反射执行业务 → 原路返回
```

用 **"10 个环节、5 个关键字"** 来记：

| # | 环节 | 关键字 | 一句话 |
|---|------|--------|--------|
| 1 | 代理拦截 | **Proxy** | JDK/Javassist 代理把方法调用转为 Invocation |
| 2 | Filter 链 | **Filter** | 监控、日志等横切逻辑自动执行 |
| 3 | 集群容错 | **Cluster** | Directory 查列表 → LoadBalance 选一个 → 失败重试 |
| 4 | 协议编码 | **Codec** | 加 Dubbo 协议头（0xdabb）→ 序列化为字节 |
| 5 | Netty 发送 | **Transport** | NIO 异步发送，Future 阻塞等待响应 |
| 6 | Netty 接收 | **Transport** | 帧解码器解包 → 协议解码 → 反序列化 |
| 7 | 请求分发 | **Protocol** | 根据接口名查找 Exporter → 获取 Invoker |
| 8 | Filter 链 | **Filter** | Provider 端的日志、异常包装、监控 |
| 9 | 反射调用 | **Invoker** | 反射执行真正的业务实现类 |
| 10 | 原路返回 | — | Response → 编码 → Netty → Future 唤醒 |

---

## 二、分层版（2分钟，展示架构理解）

> 面试官：能详细说说吗？

```
消费者端（自上而下）               提供者端（自下而上）
═══════════════                   ═══════════

┌─ 业务代码 ─┐                     ┌─ 业务实现 ─┐
│ getUser()  │                     │ UserServiceImpl │
└─────┬──────┘                     └──────▲──────┘
      │                                   │
┌─────▼──────┐ 代理层                ┌──────┴──────┐ 代理层
│ JDK Proxy  │ 方法→RpcInvocation    │ 反射调用     │ getMethod+invoke
└─────┬──────┘                      └──────▲──────┘
      │                                   │
┌─────▼──────┐ Filter层              ┌──────┴──────┐ Filter层
│ Monitor    │ 监控统计               │ AccessLog   │ 访问日志
│ TimeCost   │ 耗时记录               │ Exception   │ 异常包装
└─────┬──────┘                      └──────▲──────┘
      │                                   │
┌─────▼──────┐ 集群层                ┌──────┴──────┐ 协议层
│ Directory  │ 查服务列表             │ DubboProtocol│ 请求分发
│ LoadBalance│ 选一个Provider         │ handle()    │ 找Exporter
│ Cluster    │ 失败重试               └──────▲──────┘
└─────┬──────┘                            │
      │                                   │
┌─────▼──────┐ 协议层                ┌──────┴──────┐ 传输层
│ DubboCodec │ 加协议头+序列化        │ NettyServer │ NIO接收
└─────┬──────┘                      └──────▲──────┘
      │                                   │
┌─────▼──────┐ 传输层                    │
│ NettyClient│ NIO发送            ═══════╧═══════
└─────┬──────┘                    网络传输
      │
      │ ═══════ TCP ═══════→
```

**重点强调 3 个设计亮点**：

1. **上下对称**：Consumer 端自上而下 6 层，Provider 端自下而上 6 层，一一对应
2. **Filter 链在两端**：Consumer 端也有 Filter，Provider 端也有 Filter，各自独立
3. **Proxy 对 Proxy**：Consumer 的 JDK 代理 ↔ Provider 的反射调用，接口统一为 Invoker

---

## 三、精讲版（5分钟，展示源码深度）

> 面试官：能深入讲讲每个环节的实现吗？

### Consumer 端（5 步）

**第 1 步：代理拦截**

```java
// 你写的代码
IUserService service = referenceConfig.get();
User user = service.getUser(1001L);

// 实际执行的是代理对象 $Proxy0
// $Proxy0.getUser(1001L)
//   → InvokerInvocationHandler.invoke(proxy, method, args)
//     → 构建 RpcInvocation {接口名:"IUserService", 方法:"getUser", 参数:[1001L]}
```

> **面试要点**：Dubbo 默认用 Javassist 生成代理（比 JDK 快，且不需要接口），原理一致。

---

**第 2 步：Filter 链**

```java
// 所有 @Activate(group="consumer") 的 Filter 按 order 排序后链式调用
// MonitorFilter(order=MAX) → TimeCostFilter(order=100) → 下一个
public Result invoke(Invoker<?> invoker, Invocation inv) {
    long start = now();                    // 前置处理
    Result result = invoker.invoke(inv);   // 调用下一个 Filter
    stats.record(now() - start);           // 后置处理
    return result;
}
```

> **面试要点**：Filter 链就是责任链模式，每个 Filter 持有"下一个 Invoker"的引用，调用 `invoker.invoke()` 就是交给下一个。

---

**第 3 步：集群容错**

```java
// FailoverClusterInvoker.doInvoke()
List<Invoker<T>> invokers = directory.list(invocation);  // 从注册中心拿到 [P1, P2, P3]
Invoker<T> target = loadBalance.select(invokers, ...);   // 加权随机选 P1

for (int i = 0; i <= retries; i++) {
    try {
        Result result = target.invoke(invocation);  // 调用 P1
        return result;  // 成功就返回
    } catch (Exception e) {
        target = selectAnother();  // 失败换一个
    }
}
throw new RpcException("全部失败，重试" + retries + "次");
```

> **面试要点**：
> - Directory 从注册中心实时同步 Provider 列表
> - LoadBalance 默认加权随机（还有轮询/最少活跃/一致性哈希）
> - retries=2 表示最多调 3 次（1 次原始 + 2 次重试），不会重复调同一个失败的 Provider

---

**第 4 步：协议编码**

```java
// DubboCodec.encode(request)
byte[] body = serialization.serialize(request);  // 序列化 Request
// 写入 16 字节协议头:
// ┌────────┬──────┬────────┬──────────┬──────────┬──────────┐
// │ 0xdabb │ Flag │ Status │   ReqID  │ Body Len │   Body   │
// │  2B    │  1B  │  1B    │   8B     │   4B     │  N bytes │
// └────────┴──────┴────────┴──────────┴──────────┴──────────┘
```

> **面试要点**：
> - 魔数 0xdabb 用于快速识别 Dubbo 协议消息
> - Flag 字节的低 3 位是序列化 ID（0=JDK, 1=Hessian2, 2=Fastjson, 3=Kryo）
> - Body Length 解决 TCP 粘包/拆包

---

**第 5 步：Netty 异步发送 + 同步等待**

```java
// 注册 Future
Map<Long, CompletableFuture<Response>> pendingRequests;
pendingRequests.put(request.getId(), future);

// 异步发送
channel.writeAndFlush(request);

// 同步阻塞等待
Response response = future.get(timeout, TimeUnit.MILLISECONDS);
// → 当 Provider 返回时，NettyClientHandler 从 pendingRequests 取出 Future 并 complete()
```

> **面试要点**：发送是异步的（不阻塞 IO 线程），但业务层是同步的（通过 Future.get 阻塞等待）。这个 "异步 → 同步" 的转换是 Exchange 层（Step 17）的核心。

---

### Provider 端（镜像 5 步）

**第 6-7 步：Netty 接收 → 协议解码 → 请求分发**

```java
// LengthFieldBasedFrameDecoder 读取长度前缀，拼出完整帧
// DubboCodec.decode(bytes) → 校验 0xdabb → 反序列化 → Request
// DubboProtocol.handle(request) → 构建 RpcInvocation → findExporter("IUserService")
```

**第 8-9 步：Provider Filter 链 → 反射调用**

```java
// AccessLogFilter → ExceptionFilter → MonitorFilter → 反射
Method method = UserServiceImpl.class.getMethod("getUser", Long.class);
Object result = method.invoke(userServiceImpl, 1001L);
// → User{id=1001, name="User_1001"}
```

**第 10 步：原路返回**

```java
Response.success(1L, user)
  → DubboCodec.encode(response)
  → Netty 发送
  → Consumer 端 pendingRequests.get(1).complete(response)
  → future.get() 解除阻塞
  → Result → User → 返回到业务代码
```

---

## 四、一句话记忆法

把 10 个环节压缩成一句话，面试时顺着展开：

> **"代理拦，Filter 过，集群选，协议编，Netty 发，**
>  **Netty 收，协议解，Filter 再过，反射调，原路回。"**

| 口诀 | 对应环节 | 核心类 |
|------|---------|--------|
| 代理拦 | 动态代理拦截方法调用 | `InvokerInvocationHandler` |
| Filter 过 | Consumer 端 Filter 链 | `MonitorFilter`, `TimeCostFilter` |
| 集群选 | Directory + LoadBalance + 容错 | `FailoverClusterInvoker` |
| 协议编 | 加 Dubbo 协议头 + 序列化 | `DubboCodec` |
| Netty 发 | NIO 异步发送 | `NettyClient` |
| Netty 收 | 帧解码 + 协议解码 | `NettyServer` |
| 协议解 | 反序列化 Request | `DubboCodec` |
| Filter 再过 | Provider 端 Filter 链 | `AccessLogFilter`, `ExceptionFilter` |
| 反射调 | 反射执行实现类 | `AbstractProxyInvoker` |
| 原路回 | Response → 编码 → 返回 | 后半段全是逆过程 |

---

## 五、面试中的加分细节

当面试官追问时，下面这些点能让你脱颖而出：

**Q: Filter 链怎么构建的？**

> "通过 SPI 的 @Activate 注解。比如 @Activate(group="provider") 的 Filter 在 Provider 端自动激活，按 order 排序后从后往前构建链表——最后面的 Filter 最先执行前置，最后执行后置。这是典型的责任链模式。"

**Q: 为什么 Consumer 端也要有 Filter？**

> "Consumer 端的 Filter 在请求发出前执行，比如 MonitorFilter 记录开始时间、TokenFilter 做鉴权。Provider 端的 Filter 在请求收到后执行，比如 ExceptionFilter 统一包装异常。两端职责不同。"

**Q: 注册中心挂了影响调用吗？**

> "不影响已有调用。Directory 在本地缓存了 Provider 列表。注册中心不可用时，Consumer 仍能调用已知的 Provider，只是无法感知 Provider 上下线。这是 Dubbo 的容灾设计。"

**Q: 整个调用链中最耗时的是哪一步？**

> "网络传输。我们每个环节都有 MonitorFilter 统计耗时：Consumer 端的总耗时减去 Provider 端的处理耗时，就是网络往返时间。"

---

## 六、完整时序总结

```
时间轴 ──────────────────────────────────────────────────→

Consumer                                                Provider
   │                                                       │
   │ ① Proxy 拦截方法调用                                    │
   │ ② Consumer Filter 链(监控+耗时)                         │
   │ ③ 集群容错(Directory+LB+重试)                           │
   │ ④ 协议编码(Dubbo头+序列化)                              │
   │ ⑤ Netty 发送 ─────── TCP 446 bytes ──────────────→    │
   │    (异步等待 Future.get())                              │
   │                                                       │ ⑥ Netty 帧解码
   │                                                       │ ⑦ 协议解码(验0xdabb+反序列化)
   │                                                       │ ⑧ 请求分发(findExporter)
   │                                                       │ ⑨ Provider Filter链(日志+异常+监控)
   │                                                       │ ⑩ 反射调用 UserServiceImpl
   │                                                       │ ⑪ 业务执行 getUser(1001L)
   │                                                       │ ⑫ 响应编码(Dubbo头+序列化)
   │  ⑬ Netty 收 ←──────── TCP 246 bytes ───────────────  │ ⑬ Netty 回
   │  ⑭ 协议解码(反序列化 Response)                           │
   │  ⑮ Future 唤醒                                         │
   │  ⑯ Consumer Filter 后置(耗时+监控)                      │
   │  ⑰ 返回 User 给业务代码                                  │
   │                                                       │
   ▼                                                       ▼
User user = User{name="User_1001"}               return new User(...)
```

**时间分布（典型值）**：
- ①-③ 本地处理：< 1ms
- ④-⑤ 编码+发送：< 1ms
- ⑤-⑬ 网络往返：1-100ms（取决于网络）
- ⑬-⑰ 解码+返回：< 1ms
- **瓶颈永远在网络，不在框架本身。**
