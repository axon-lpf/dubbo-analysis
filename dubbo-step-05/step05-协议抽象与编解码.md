# Step 05：协议抽象与 Dubbo 协议编解码

## 一、本步骤解决的问题

**核心问题：如何将传输层与协议层解耦？如何实现 Dubbo 协议格式？**

Step 01-04 中，传输层（ExporterServer/ObjectClient）和协议逻辑混在一起，用的是简单的"长度前缀"帧协议。Step 05 将正式引入：

1. **Dubbo 协议头格式**（16 字节标准头 + 变长数据体）
2. **Protocol 接口**（export / refer 标准 API）
3. **DubboInvoker**（Consumer 端远程调用 Invoker）
4. **协议层与代理层的整合**（ProxyFactory 直接操作 Invoker）

## 二、新增了哪些能力

- ✅ `Codec` 接口 — 编解码器抽象
- ✅ `DubboCodec` — Dubbo 协议编解码实现（16 字节协议头）
- ✅ `Constants` — Dubbo 协议常量（魔数 0xdabb）
- ✅ `Protocol` 接口 — export/refer 标准 API
- ✅ `DubboProtocol` — Dubbo 协议实现
- ✅ `DubboInvoker` — Consumer 端远程调用 Invoker
- ✅ `ProxyFactory` 升级 — getProxy(Invoker) 替代 createProxy(Class, ObjectClient)
- ✅ `InvokerInvocationHandler` 升级 — 持有 Invoker 而非 ObjectClient

## 三、核心类一览

| 类名 | 作用 | Dubbo 对应 |
|------|------|-----------|
| `Codec` | 编解码接口 | `org.apache.dubbo.remoting.Codec2` |
| `DubboCodec` | Dubbo 协议编解码（16B 头 + 数据体） | `org.apache.dubbo.remoting.exchange.codec.ExchangeCodec` |
| `Constants` | 魔数/标志位/状态码 | `org.apache.dubbo.common.constants.CommonConstants` |
| `Protocol` | 协议接口（export/refer） | `org.apache.dubbo.rpc.Protocol`（@SPI） |
| `DubboProtocol` | Dubbo 协议实现 | `org.apache.dubbo.rpc.protocol.dubbo.DubboProtocol` |
| `DubboInvoker` | Consumer 端远程 Invoker | `org.apache.dubbo.rpc.protocol.dubbo.DubboInvoker` |

## 四、核心原理剖析

### 4.1 Dubbo 协议消息格式

```
┌─────────┬─────────┬─────────┬─────────────────┬─────────────────┐
│ 偏移    │ 大小    │ 字段    │ 说明             │ 示例             │
├─────────┼─────────┼─────────┼─────────────────┼─────────────────┤
│ 0-1     │ 2 bytes │ Magic   │ 魔数 0xdabb      │ 0xda 0xbb       │
│ 2       │ 1 byte  │ Flag    │ 请求/响应/单向    │ 0x00(请求)      │
│ 3       │ 1 byte  │ Status  │ 状态码(响应时有效) │ 20(OK) / 30(Err)│
│ 4-11    │ 8 bytes │ Request │ 请求 ID(long)     │ 0x00...01       │
│         │         │ ID      │                   │                 │
│ 12-15   │ 4 bytes │ Body    │ 数据体长度(int)    │ 0x00...1A3      │
│         │         │ Length  │                   │                 │
├─────────┼─────────┼─────────┼─────────────────┼─────────────────┤
│ 16-N    │ N bytes │ Body    │ 序列化后的数据体   │ 实际 Request/   │
│         │         │         │                   │ Response 字节   │
└─────────┴─────────┴─────────┴─────────────────┴─────────────────┘
```

**Flag 标志位定义：**

| 位 | 值 | 含义 |
|----|-----|------|
| bit 7 | 0x80 | 响应标志（=1 为响应） |
| bit 6 | 0x40 | 单向标志（不需要返回值） |
| bit 0-2 | 0x07 | 序列化 ID（0=JDK, 1=Hessian2, ...） |

**Status 状态码：**

| 值 | 含义 |
|-----|------|
| 20 | OK —— 调用成功 |
| 30 | CLIENT_ERROR —— 客户端错误 |
| 31 | SERVER_ERROR —— 服务端错误 |

### 4.2 编解码流程

```
【编码：Request → 字节数组】

1. 序列化 Request body
   byte[] body = serialization.serialize(request);

2. 写入协议头（16 字节）
   dos.writeShort(0xdabb);      // Magic
   dos.writeByte(0x00);         // Flag: 请求
   dos.writeByte(0x00);         // Status: 请求时无意义
   dos.writeLong(requestId);    // Request ID
   dos.writeInt(body.length);   // Body Length

3. 写入数据体
   dos.write(body);

4. 结果：16 + body.length 字节


【解码：字节数组 → Request】

1. 校验魔数
   if (magic != 0xdabb) → 不是 Dubbo 协议消息！

2. 解析标志位
   isResponse = (flag & 0x80) != 0
   isOneWay   = (flag & 0x40) != 0

3. 读取请求 ID 和 Body Length

4. 读取并反序列化 Body
   serialization.deserialize(body, isResponse ? Response.class : Request.class)
```

### 4.3 Protocol 体系 —— Provider 端

```
┌───────────────────────────────────────────────────┐
│ Protocol.export(Invoker) → Exporter               │
│                                                    │
│ DubboProtocol.export():                            │
│  1. 创建 DubboExporter(invoker)                     │
│  2. 注册到 exporterMap（serviceKey → Exporter）     │
│  3. 启动 ServerSocket 监听 URL port                 │
│  4. 返回 exporter                                  │
│                                                    │
│ 请求到达时：                                       │
│  Socket.accept()                                   │
│    → 读取长度前缀 + 协议消息                         │
│    → DubboCodec.decode(bytes) → Request            │
│    → 查找 Exporter → Invoker.invoke(invocation)    │
│    → Result → Response                             │
│    → DubboCodec.encode(response) → bytes           │
│    → 发送长度前缀 + 响应消息                         │
└───────────────────────────────────────────────────┘
```

### 4.4 Protocol 体系 —— Consumer 端

```
┌───────────────────────────────────────────────────┐
│ Protocol.refer(Class<T>, URL) → Invoker<T>         │
│                                                    │
│ DubboProtocol.refer():                             │
│  1. 创建 DubboInvoker(type, url)                   │
│  2. 返回 invoker                                   │
│                                                    │
│ ProxyFactory.getProxy(invoker) → Proxy:            │
│  → InvokerInvocationHandler.invoke()               │
│    → 构建 RpcInvocation                            │
│    → Invoker.invoke(invocation)                    │
│      → DubboInvoker.doInvoke()                     │
│        1. 构建 Request                             │
│        2. DubboCodec.encode(request)               │
│        3. Socket.connect(url.host:port)            │
│        4. 发送长度前缀 + 协议消息                    │
│        5. 接收长度前缀 + 协议消息                    │
│        6. DubboCodec.decode(response)              │
│        7. Response → RpcResult                     │
│      ← Result                                     │
│    ← result.recreate() → 返回给调用方              │
└───────────────────────────────────────────────────┘
```

### 4.5 关键架构升级：代理层不再依赖 ObjectClient

```
Step 04:                                      Step 05:
════════                                      ═══════

ProxyFactory.createProxy(Class, ObjectClient)  ProxyFactory.getProxy(Invoker)
        │                                             │
InvokerInvocationHandler                           InvokerInvocationHandler
  ├─ interfaceClass                                  ├─ invoker (Invoker 引用)
  ├─ client (ObjectClient)                           │
  └─ invoke():                                       └─ invoke():
      1. buildInvocation(method, args)                   1. buildInvocation(method, args)
      2. Request request = new Request(...)              2. Result result = invoker.invoke(invocation)
      3. Response = client.send(request)                 3. return result.recreate()
      4. return response.getResult()
```

**关键变化：**
- `InvokerInvocationHandler` 不再构建 `Request`，不关心序列化和网络
- 它将所有调用委托给 `Invoker.invoke()`，Invoker 可以是：
  - `DubboInvoker`：网络 + 序列化
  - `AbstractProxyInvoker`：本地反射（测试/本地调用场景）
  - `FailoverClusterInvoker`：集群容错（Step 11）
  - `Filter Wrapper`：过滤器链（Step 14）

### 4.6 当前架构全景（Step 05）

```
Consumer 端                                   Provider 端
═══════════                                   ═══════════

IUserService proxy                            new UserServiceImpl()
       │                                              │
  ProxyFactory.getProxy(invoker)              ProxyFactory.getInvoker(impl, type, url)
       │                                              │
  InvokerInvocationHandler                    AbstractProxyInvoker
       │                                              │
  Invoker.invoke(invocation)                  Protocol.export(invoker) → Exporter
       │                                              │
  DubboInvoker.doInvoke()                     DubboProtocol.export()
    ├─ Request                                    ├─ exporterMap.put(...)
    ├─ DubboCodec.encode(request)                   ├─ ServerSocket 监听
    ├─ Socket → Provider                        ├─ accept() 等待请求
    ├─ Socket ← Provider                        ├─ DubboCodec.decode(bytes)
    ├─ DubboCodec.decode(bytes)                    ├─ findExporter(name)
    └─ Response → Result                     ├─ Invoker.invoke(invocation)
                                               ├─ DubboCodec.encode(response)
                                               └─ Socket ← Consumer
```

## 五、面试常见问法

**Q: Dubbo 协议的报文格式是怎样的？**
A: Dubbo 协议报文 = 16 字节协议头 + 变长数据体。协议头包含：魔数 0xdabb（2B）、标志位（1B）、状态码（1B）、请求 ID（8B）、数据长度（4B）。数据体是序列化后的 Request 或 Response 对象。

**Q: Dubbo 协议的魔数有什么作用？**
A: 魔数 0xdabb 用于快速识别是否是 Dubbo 协议消息。接收方先校验魔数，不匹配说明不是 Dubbo 协议报文（可能是其他协议或攻击流量），直接拒绝或切换协议解析器。

**Q: Protocol 接口的 export 和 refer 分别做什么？**
A: `export()` 是 Provider 端操作：将 Invoker 暴露到网络上（开启端口、注册到服务表）；`refer()` 是 Consumer 端操作：根据 URL 创建远程调用的 Invoker。Protocol 是 Dubbo 架构的"腰"，向上提供稳定接口，向下适配多种协议。

**Q: InvokerInvocationHandler 为什么改成持有 Invoker 而不是 ObjectClient？**
A: 这是关键的抽象升级。持有 Invoker 后，代理层不再关心底层是网络调用还是本地调用。所有 Invoker 实现（远程/本地/集群/过滤器包装）都遵循同一个接口，代理层无需感知。这就是"面向接口编程"的价值。

## 六、快速复习入口

| 想了解的内容 | 文件路径 |
|-------------|---------|
| 协议常量 | `src/main/java/.../common/Constants.java` |
| 编解码接口 | `src/main/java/.../remoting/Codec.java` |
| Dubbo 协议编解码 | `src/main/java/.../remoting/exchange/DubboCodec.java` |
| 协议接口 | `src/main/java/.../rpc/protocol/Protocol.java` |
| Dubbo 协议实现 | `src/main/java/.../rpc/protocol/DubboProtocol.java` |
| Consumer 端 Invoker | `src/main/java/.../rpc/protocol/DubboInvoker.java` |
| 升级后的代理工厂 | `src/main/java/.../rpc/proxy/ProxyFactory.java` |
| 升级后的拦截器 | `src/main/java/.../rpc/proxy/jdk/InvokerInvocationHandler.java` |
| 单元测试 | `src/test/java/.../demo/ApiTest.java` |
