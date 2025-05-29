收到，大飞哥！接下来严格按照你的目录，进入**第二章：构建第一个手写RPC通信框架**，本章聚焦于**代码实现**，以最简单直接的方式，动手搭建“Dubbo式RPC的最小原型”，即：**基于Socket的客户端/服务端交互、接口定义、服务注册与获取**，实现服务提供者与消费者的最基础Demo。

---

# 第二章 构建第一个手写RPC通信框架

---

## 2.1 基于Socket实现最简单的客户端与服务端交互

### 2.1.1 目标

* 用最朴素的Java代码（不引入任何第三方通信框架），手写出一个**最小可用的RPC通信模型**；
* 让客户端像调用本地方法一样去调用远程服务；
* 培养“Dubbo一切皆Invoker、协议、服务导出与引用”思想的基础认知。

---

### 2.1.2 项目结构建议

```
handwritten-dubbo
└── chapter02-simple-rpc
    ├── api
    │   └── HelloService.java
    ├── provider
    │   └── ProviderServer.java
    │   └── HelloServiceImpl.java
    └── consumer
        └── ConsumerClient.java
```

---

### 2.1.3 核心代码详解

#### 1. 定义公共服务接口

```java
// api/HelloService.java
package api;

public interface HelloService {
    String sayHello(String name);
}
```

#### 2. 服务端实现服务

```java
// provider/HelloServiceImpl.java
package provider;

import api.HelloService;

public class HelloServiceImpl implements HelloService {
    @Override
    public String sayHello(String name) {
        return "你好，" + name + "！来自手写Dubbo的问候~";
    }
}
```

#### 3. 服务端暴露服务

```java
// provider/ProviderServer.java
package provider;

import api.HelloService;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class ProviderServer {
    public static void main(String[] args) throws Exception {
        ServerSocket serverSocket = new ServerSocket(12345);
        System.out.println("服务端启动，监听端口 12345...");
        HelloService helloService = new HelloServiceImpl();

        while (true) {
            Socket socket = serverSocket.accept();
            new Thread(() -> {
                try (
                    ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())
                ) {
                    // 读取客户端传来的方法名和参数
                    String method = (String) in.readObject();
                    String arg = (String) in.readObject();

                    String result = null;
                    // 只实现单一方法路由
                    if ("sayHello".equals(method)) {
                        result = helloService.sayHello(arg);
                    }
                    // 返回结果
                    out.writeObject(result);
                    out.flush();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try { socket.close(); } catch (Exception ignore) {}
                }
            }).start();
        }
    }
}
```

#### 4. 客户端调用服务

```java
// consumer/ConsumerClient.java
package consumer;

import java.io.*;
import java.net.Socket;

public class ConsumerClient {
    public static void main(String[] args) throws Exception {
        Socket socket = new Socket("localhost", 12345);

        try (
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream())
        ) {
            // 发送方法名和参数
            out.writeObject("sayHello");
            out.writeObject("大飞哥");
            out.flush();

            // 接收并输出结果
            String result = (String) in.readObject();
            System.out.println("收到服务端返回：" + result);
        }
    }
}
```

---

## 2.2 服务提供者/服务消费者接口与Demo

### 2.2.1 启动测试步骤

1. 先启动`ProviderServer.java`，控制台输出“服务端启动，监听端口12345...”。
2. 再运行`ConsumerClient.java`，控制台会输出：“收到服务端返回：你好，大飞哥！来自手写Dubbo的问候\~”

---

### 2.2.2 代码设计要点

* **接口、实现、服务端、客户端**层次分明，和Dubbo源码架构的“Provider/Consumer”抽象完全一致。
* 只用最基础的Socket、ObjectInputStream/ObjectOutputStream完成Java对象传输，易于理解和后续改造。
* 这是Dubbo所有高级特性的“起点”，下一章将基于这套代码逐步引入“动态代理”等Dubbo的核心解耦思想。

---

## 2.3 本章小结

* 已经构建出一个最原始的“Dubbo式”RPC通信Demo；
* 为后续“透明代理、协议抽象、注册中心”等特性实现，打下实战基础。

---

**下一章预告**：
第三章将围绕“动态代理”展开，让服务调用过程更加Dubbo化——接口调用**透明化**，无需手写网络细节！

---
