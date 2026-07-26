# Step 16：Netty 传输层（BIO → NIO）

## 一、本步骤解决的问题

**核心问题：如何用高性能 NIO 替换 BIO Socket？**

Step 01-14 的传输层一直用 Java 原生 `ServerSocket`/`Socket`（BIO 阻塞模型）：
- 每个连接需要一个线程处理
- 连接空闲时线程也阻塞等待
- 无法支撑高并发场景

Step 16 用 Netty（NIO 多路复用）替换底层传输，而**上层代码零改动**——这正是抽象层的价值。

```
Step 01-14 (BIO):                      Step 16 (NIO):
═══════════════                        ═══════════

ServerSocket.accept() 阻塞              NioEventLoopGroup 非阻塞多路复用
Socket readLine() 阻塞                  Netty Channel Pipeline 异步处理
1 连接 → 1 线程                        1 EventLoop → N 个 Channel
每次请求新建 Socket                     长连接 + Channel 复用
```

## 二、新增了哪些能力

- ✅ `NettyServer` — NIO 服务端（NioEventLoopGroup + Pipeline）
- ✅ `NettyClient` — NIO 客户端（长连接 + Channel 复用）
- ✅ `NettyInvoker` — 使用 NettyClient 的远程 Invoker
- ✅ `DubboProtocol` 升级 — NettyServer 代替 ServerSocket
- ✅ `LengthFieldBasedFrameDecoder` — Netty 帧解码（替代手动长度前缀）

## 三、核心类一览

| 类名 | 作用 | Dubbo 对应 |
|------|------|-----------|
| `NettyServer` | NIO 服务端 | `org.apache.dubbo.remoting.transport.netty4.NettyServer` |
| `NettyClient` | NIO 客户端（长连接复用） | `org.apache.dubbo.remoting.transport.netty4.NettyClient` |
| `NettyInvoker` | Netty 远程 Invoker | —（替代 DubboInvoker） |
| `DubboProtocol` (升级) | 集成 Netty | `org.apache.dubbo.rpc.protocol.dubbo.DubboProtocol` |

## 四、核心原理剖析

### 4.1 Netty Pipeline 结构

```
┌─────────────────────────────────────────┐
│         Netty Channel Pipeline           │
├─────────────────────────────────────────┤
│                                          │
│  LengthFieldBasedFrameDecoder            │
│  ├── 读前 4 字节 → 数据长度               │
│  ├── 读取 N 字节 → 完整帧                 │
│  └── 解决 TCP 粘包/拆包 ✓                 │
│                                          │
│  NettyCodecHandler (ChannelDuplexHandler) │
│  ├── channelRead: bytes → DubboCodec.decode → Request
│  └── write: Response → DubboCodec.encode → bytes + length
│                                          │
│  NettyServerHandler / NettyClientHandler  │
│  └── 业务处理: Request → process → Response │
│                                          │
└─────────────────────────────────────────┘
```

### 4.2 BIO vs NIO 对比

```
BIO (ServerSocket):                     NIO (Netty):
══════════════════                       ════════════

while (running) {                       bossGroup (1 thread)
    Socket client = accept();  ← 阻塞    └→ accept connections
    new Thread(() -> {                   workerGroup (N threads)
        read(client);                    └→ handle read/write
        process();                          多路复用 1:N
        write(client);
    }).start();
}
```

### 4.3 NettyClient 连接复用

```java
// 按 host:port 缓存 NettyClient，复用连接
static final Map<String, NettyClient> CLIENT_CACHE = new ConcurrentHashMap<>();

NettyClient client = CLIENT_CACHE.computeIfAbsent(key, k -> {
    NettyClient c = new NettyClient(host, port, codec);
    c.connect();  // 建立 TCP 长连接
    return c;
});
// 同一个 host:port 的所有请求共享一条 TCP 连接
```

### 4.4 请求-响应匹配（异步 → 同步）

```java
// Map<requestId, CompletableFuture<Response>>
Map<Long, CompletableFuture<Response>> pendingRequests;

// 发送端
public Response send(Request request) {
    CompletableFuture<Response> future = new CompletableFuture<>();
    pendingRequests.put(request.getId(), future);
    channel.writeAndFlush(request);  // 异步发送
    return future.get();             // 同步阻塞等待
}

// 接收端
protected void channelRead0(ctx, Response response) {
    CompletableFuture<Response> future = pendingRequests.remove(response.getId());
    if (future != null) future.complete(response);  // 唤醒阻塞
}
```

### 4.5 Netty 线程模型

```
                    ┌─────────────┐
                    │ bossGroup   │
                    │ (1 thread)  │  ← accept 连接
                    └──────┬──────┘
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
        ┌─────────┐  ┌─────────┐  ┌─────────┐
        │EventLoop│  │EventLoop│  │EventLoop│  ← workerGroup (N threads)
        │ (read)  │  │ (read)  │  │ (read)  │    处理 I/O
        │ (write) │  │ (write) │  │ (write) │
        └────┬────┘  └────┬────┘  └────┬────┘
             │            │            │
        Channel1,2    Channel3,4    Channel5,6
       (1:N 复用)    (1:N 复用)    (1:N 复用)
```

### 4.6 上层透明——BIO→NIO 零改动

```
Step 14 (BIO)                  Step 16 (NIO)
═══════════                    ═══════════

userService.getUser(1L)        userService.getUser(1L)
  → Proxy                        → Proxy          ← 不变
  → Filter                       → Filter         ← 不变
  → ClusterInvoker               → ClusterInvoker ← 不变
  → DubboInvoker                 → NettyInvoker   ← 内部变了
    └ new Socket()                 └ NettyClient.send()
    └ readLine()                   └ CompletableFuture.get()
```

## 五、面试常见问法

**Q: Dubbo 为什么选择 Netty 作为默认传输层？**
A: Netty 是基于 NIO 的高性能网络框架。相比 BIO，NIO 使用 Selector 多路复用，一个线程可以管理多个连接，大幅降低线程资源消耗。Netty 还提供了完善的编解码、心跳、超时等机制，以及零拷贝、内存池等性能优化。

**Q: Netty 的 LengthFieldBasedFrameDecoder 解决了什么问题？**
A: TCP 是流式协议，发送方连续发送的数据可能在接收方被合并（粘包）或拆分（拆包）。LengthFieldBasedFrameDecoder 通过长度前缀字段（4 字节 int）精确分割每一帧，解决了粘包/拆包问题。

**Q: Dubbo 的 Consumer 端是如何复用 Netty 连接的？**
A: Dubbo 按 Provider 地址（host:port）缓存 NettyClient 实例。对同一个 Provider 的多次 RPC 调用共享同一条 TCP 连接（长连接），避免了频繁建立/断开连接的开销。

## 六、快速复习入口

| 想了解的内容 | 文件路径 |
|-------------|---------|
| Netty 服务端 | `src/main/java/.../remoting/transport/netty/NettyServer.java` |
| Netty 客户端 | `src/main/java/.../remoting/transport/netty/NettyClient.java` |
| Netty Invoker | `src/main/java/.../rpc/protocol/NettyInvoker.java` |
| 升级后的协议 | `src/main/java/.../rpc/protocol/DubboProtocol.java` |
| 单元测试 | `src/test/java/.../demo/ApiTest.java` |
