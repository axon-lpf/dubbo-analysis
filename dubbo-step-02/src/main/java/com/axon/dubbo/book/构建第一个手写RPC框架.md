好的，我们现在进入：

---

# 📘 第二章 构建第一个手写 RPC 框架（基于 Socket 通信）

---

## 一、章节目标

本章将在第一章原理的基础上，用最基础的 Java Socket 实现一个**端到端的 RPC 调用流程**，形成最简版的通信原型。

**目标能力：**

* 定义服务接口与实现类
* 搭建服务端监听请求
* 客户端远程调用 Socket 连接服务端
* 模拟接口调用（参数传递 + 返回结果）

---

## 二、项目结构初始化（新增源码结构）

```plaintext
simple-rpc/
├── rpc-api/                 // 公共模块：接口定义
│   └── HelloService.java    // 示例服务接口
├── rpc-core/                // 暂空，下一章引入动态代理等
├── rpc-provider/            // 服务端模块
│   ├── ProviderServer.java
│   └── HelloServiceImpl.java
├── rpc-consumer/            // 客户端模块
│   └── ConsumerClient.java
└── README.md
```

---

## 三、代码详解

### 1. 定义公共接口模块：`rpc-api`

**HelloService.java**

```java
package org.simple.rpc.api;

public interface HelloService {
    String sayHello(String name);
}
```

---

### 2. 服务端实现模块：`rpc-provider`

**HelloServiceImpl.java**

```java
package org.simple.rpc.provider;

import org.simple.rpc.api.HelloService;

public class HelloServiceImpl implements HelloService {
    @Override
    public String sayHello(String name) {
        return "Hello, " + name + "! (from provider)";
    }
}
```

---

**ProviderServer.java**

```java
package org.simple.rpc.provider;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class ProviderServer {

    public static void main(String[] args) throws IOException {
        HelloService helloService = new HelloServiceImpl();
        ServerSocket serverSocket = new ServerSocket(9000);
        System.out.println("服务端启动，监听端口 9000...");

        while (true) {
            Socket socket = serverSocket.accept();
            new Thread(() -> handleClient(socket, helloService)).start();
        }
    }

    private static void handleClient(Socket socket, HelloService service) {
        try (
            ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
            ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())
        ) {
            // 读取方法参数
            String methodName = (String) input.readObject(); // sayHello
            String argument = (String) input.readObject();   // name

            if ("sayHello".equals(methodName)) {
                String result = service.sayHello(argument);
                output.writeObject(result);
                output.flush();
            } else {
                output.writeObject("Unsupported method: " + methodName);
                output.flush();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

---

### 3. 客户端模块：`rpc-consumer`

**ConsumerClient.java**

```java
package org.simple.rpc.consumer;

import java.io.*;
import java.net.Socket;

public class ConsumerClient {

    public static void main(String[] args) {
        try (
            Socket socket = new Socket("localhost", 9000);
            ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream input = new ObjectInputStream(socket.getInputStream())
        ) {
            // 模拟远程调用
            output.writeObject("sayHello");
            output.writeObject("World");

            // 接收响应
            String result = (String) input.readObject();
            System.out.println("客户端收到返回值：" + result);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

---

## 四、运行流程说明

1. 启动 `ProviderServer`，服务端监听端口 `9000`
2. 启动 `ConsumerClient`，向服务端发送 `sayHello("World")` 请求
3. 服务端处理请求并返回结果，客户端打印响应

---

## 五、当前局限与问题

* 无法支持多个接口或方法（方法名是手动写死的）
* 客户端必须自己处理调用细节（无代理）
* 无协议格式、无序列化抽象

> 我们将在后续逐步引入：**动态代理 → Netty 网络通信 → 协议封装 → 序列化扩展**

---