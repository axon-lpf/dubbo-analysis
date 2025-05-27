好的，下面是基于上面Netty实现的示例服务端启动代码和客户端调用代码。

---

## 服务端启动示例

```java
package org.simple.rpc.provider;

import org.simple.rpc.core.transport.NettyServer;
import org.simple.rpc.provider.impl.HelloServiceImpl;
import org.simple.rpc.api.HelloService;

public class ProviderServer {

    public static void main(String[] args) throws InterruptedException {
        int port = 8888;
        NettyServer server = new NettyServer(port);

        // 注册服务接口与实现类
        server.registerService(HelloService.class.getName(), new HelloServiceImpl());

        System.out.println("服务提供者启动，监听端口: " + port);
        server.start();
    }
}
```

---

## 客户端调用示例

```java
package org.simple.rpc.consumer;

import org.simple.rpc.api.HelloService;
import org.simple.rpc.core.codec.RpcRequest;
import org.simple.rpc.core.codec.RpcResponse;
import org.simple.rpc.core.transport.NettyClient;

public class ConsumerClient {

    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";
        int port = 8888;

        NettyClient client = new NettyClient(host, port);

        // 构造RPC请求对象
        RpcRequest request = new RpcRequest();
        request.setInterfaceName(HelloService.class.getName());
        request.setMethodName("sayHello");
        request.setParamTypes(new Class<?>[]{String.class});
        request.setParameters(new Object[]{"Netty RPC"});

        // 发送请求并接收响应
        RpcResponse response = client.sendRequest(request);

        if (response.hasException()) {
            System.err.println("调用异常: " + response.getException());
        } else {
            System.out.println("调用结果: " + response.getResult());
        }
    }
}
```

---

## 说明

* 服务端启动后，监听8888端口，等待客户端连接和请求。
* 客户端构造一个完整的`RpcRequest`，通过Netty客户端发送请求。
* 服务端根据接口名、方法名反射调用具体服务实现。
* 调用结果封装在`RpcResponse`返回给客户端。
* 客户端根据是否异常判断调用是否成功。

---
好的，下面是基于上面Netty实现的示例服务端启动代码和客户端调用代码。

---

## 服务端启动示例

```java
package org.simple.rpc.provider;

import org.simple.rpc.core.transport.NettyServer;
import org.simple.rpc.provider.impl.HelloServiceImpl;
import org.simple.rpc.api.HelloService;

public class ProviderServer {

    public static void main(String[] args) throws InterruptedException {
        int port = 8888;
        NettyServer server = new NettyServer(port);

        // 注册服务接口与实现类
        server.registerService(HelloService.class.getName(), new HelloServiceImpl());

        System.out.println("服务提供者启动，监听端口: " + port);
        server.start();
    }
}
```

---

## 客户端调用示例

```java
package org.simple.rpc.consumer;

import org.simple.rpc.api.HelloService;
import org.simple.rpc.core.codec.RpcRequest;
import org.simple.rpc.core.codec.RpcResponse;
import org.simple.rpc.core.transport.NettyClient;

public class ConsumerClient {

    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";
        int port = 8888;

        NettyClient client = new NettyClient(host, port);

        // 构造RPC请求对象
        RpcRequest request = new RpcRequest();
        request.setInterfaceName(HelloService.class.getName());
        request.setMethodName("sayHello");
        request.setParamTypes(new Class<?>[]{String.class});
        request.setParameters(new Object[]{"Netty RPC"});

        // 发送请求并接收响应
        RpcResponse response = client.sendRequest(request);

        if (response.hasException()) {
            System.err.println("调用异常: " + response.getException());
        } else {
            System.out.println("调用结果: " + response.getResult());
        }
    }
}
```

---

## 说明

* 服务端启动后，监听8888端口，等待客户端连接和请求。
* 客户端构造一个完整的`RpcRequest`，通过Netty客户端发送请求。
* 服务端根据接口名、方法名反射调用具体服务实现。
* 调用结果封装在`RpcResponse`返回给客户端。
* 客户端根据是否异常判断调用是否成功。

---