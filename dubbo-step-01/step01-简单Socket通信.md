# Step 01：简单 Socket 通信

## 一、本步骤解决的问题

**核心问题：两台 JVM 之间如何通信？**

这是 RPC（Remote Procedure Call，远程过程调用）最底层的基石。在理解 Dubbo 的复杂架构之前，我们必须先回答一个最基本的问题：数据如何在网络上的两个 Java 程序之间传递？

本步骤使用 Java 原生 Socket API 实现了最简单的"客户端 → 服务端 → 客户端"通信模型。

## 二、新增了哪些能力

- ✅ 服务端通过 `ServerSocket` 监听指定端口
- ✅ 客户端通过 `Socket` 连接到服务端
- ✅ 客户端发送请求数据（字符串形式）
- ✅ 服务端接收请求、处理、返回响应
- ✅ 客户端接收响应并展示结果

## 三、核心类一览

| 类名 | 作用 | Dubbo 中的对应概念 |
|------|------|-------------------|
| `Request` | 请求数据封装（请求 ID + 数据） | `org.apache.dubbo.remoting.exchange.Request` |
| `Response` | 响应数据封装（请求 ID + 结果） | `org.apache.dubbo.remoting.exchange.Response` |
| `SimpleServer` | 服务端：监听端口、接收连接、返回响应 | 对应 Transport 层 Server 抽象 |
| `SimpleClient` | 客户端：建立连接、发送数据、接收结果 | 对应 Transport 层 Client 抽象 |

## 四、核心原理剖析

### 4.1 通信模型（BIO）

```
Client（客户端）                          Server（服务端）
     │                                        │
     │  1. new Socket(host, port)              │
     │─────────────────────────────────────→  │  2. ServerSocket.accept()
     │                                        │     阻塞等待连接
     │  3. outputStream.write(data)            │
     │─────────────────────────────────────→  │  4. inputStream.read(data)
     │                                        │
     │                                        │  5. 处理请求
     │                                        │     processRequest(data)
     │                                        │
     │  7. inputStream.read(data)              │  6. outputStream.write(result)
     │←─────────────────────────────────────  │
     │                                        │
     │  8. socket.close()                      │  9. clientSocket.close()
     │                                        │
```

### 4.2 BIO 阻塞模型的特点

```java
// 服务端：accept() 阻塞，等待客户端连接
Socket client = serverSocket.accept();  // ← 没有连接到达时，线程阻塞在这里

// 客户端：readLine() 阻塞，等待服务端返回数据
String response = reader.readLine();    // ← 服务端没有返回时，线程阻塞在这里
```

**BIO 的缺点（后续用 Netty 解决）：**
- 每个连接需要一个线程处理（线程资源消耗大）
- 连接空闲时线程也阻塞等待（资源浪费）
- 无法支撑高并发（线程数有限）

### 4.3 关键代码解读

**服务端接收 + 处理 + 响应：**
```java
// SimpleServer.handleClient() 核心流程
BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);

// 1. 读：阻塞读取客户端发来的数据
String requestData = reader.readLine();

// 2. 算：处理请求（后续步骤这里会变成反射调用本地服务！）
String responseData = processRequest(requestData);

// 3. 写：将结果写回客户端
writer.println(responseData);
```

**客户端发送 + 接收：**
```java
// SimpleClient.send() 核心流程
Socket socket = new Socket(host, port);
PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

// 1. 写：发送请求
writer.println(request.getData());

// 2. 读：读取响应
String responseData = reader.readLine();
```

> 💡 **本质理解**：去掉所有封装，RPC 的底层就是 **"发数据 → 收数据"**。Dubbo 的 Exchanger、Transporter、Codec 等等，都是对这个过程的层层抽象和优化。

## 五、面试常见问法

**Q: Dubbo 底层是用什么通信的？**
A: Dubbo 底层默认使用 Netty 进行网络通信。Netty 是基于 Java NIO 的高性能网络框架。在项目中我们先用 Java 原生 Socket（BIO）理解通信本质，后续步骤再引入 Netty。

**Q: BIO 和 NIO 有什么区别？**
A: BIO（Blocking IO）每个连接需要一个线程，线程阻塞等待数据；NIO（Non-blocking IO）用 Selector 多路复用，一个线程可以管理多个连接。Dubbo 使用 Netty（NIO）来实现高并发通信。

**Q: 一个 RPC 请求在网络上传输的是什么？**
A: 本质上是字节流。发送端将请求对象序列化成字节数组，通过 Socket 发送；接收端通过 Socket 接收字节数组，再反序列化成请求对象。

## 六、快速复习入口

| 想了解的内容 | 文件路径 |
|-------------|---------|
| 服务端实现 | `src/main/java/.../socket/SimpleServer.java` |
| 客户端实现 | `src/main/java/.../socket/SimpleClient.java` |
| 请求对象 | `src/main/java/.../socket/Request.java` |
| 响应对象 | `src/main/java/.../socket/Response.java` |
| 单元测试 | `src/test/java/.../socket/ApiTest.java` |
