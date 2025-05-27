收到！第七章将基于第六章的内存注册中心实现，逐步迭代接入**Zookeeper作为注册中心**，完成真实分布式服务注册与订阅功能。

---

# 第七章 基于Zookeeper实现注册中心

---

## 7.1 目标与背景

* 实现注册中心从单机内存向分布式Zookeeper迁移，满足高可用、动态服务发现需求。
* 利用Zookeeper的节点数据结构实现服务注册和监听机制。
* 保持上一章节代码接口兼容，提升架构扩展性。

---

## 7.2 项目结构迭代（新增/修改）

```
simple-dubbo/
 ├─ rpc-core/
 ├─ provider/
 ├─ consumer/
 ├─ registry/
 │    ├─ Registry.java              # 接口不变
 │    ├─ InMemoryRegistry.java     # 内存实现保留
 │    ├─ ZookeeperRegistry.java    # 新增：基于Zookeeper的实现
 │    ├─ RegistryException.java
 │    └─ ZkClientWrapper.java      # 新增：Zookeeper客户端封装类
 └─ api/
```

---

## 7.3 Zookeeper基础说明

* Zookeeper以树形结构存储节点（ZNode）。
* 服务注册信息写入`/dubbo/{serviceName}/{address}`临时顺序节点。
* 客户端通过监听`/dubbo/{serviceName}`子节点变化，实现服务动态订阅。

---

## 7.4 依赖引入

`pom.xml`中引入Zookeeper和Curator客户端依赖（示例Maven）：

```xml
<dependency>
    <groupId>org.apache.curator</groupId>
    <artifactId>curator-framework</artifactId>
    <version>5.4.0</version>
</dependency>
<dependency>
    <groupId>org.apache.curator</groupId>
    <artifactId>curator-recipes</artifactId>
    <version>5.4.0</version>
</dependency>
```

---

## 7.5 代码实现

### 7.5.1 Zookeeper客户端封装 ZkClientWrapper.java

```java
package org.simple.rpc.registry;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

import java.util.List;

public class ZkClientWrapper {

    private CuratorFramework client;

    public ZkClientWrapper(String zkAddress) {
        client = CuratorFrameworkFactory.builder()
                .connectString(zkAddress)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .build();
        client.start();
    }

    public void createPersistentNode(String path) throws Exception {
        if (client.checkExists().forPath(path) == null) {
            client.create().creatingParentsIfNeeded().forPath(path);
        }
    }

    public String createEphemeralSequentialNode(String path, String data) throws Exception {
        return client.create().creatingParentsIfNeeded().withProtection()
                .withMode(org.apache.zookeeper.CreateMode.EPHEMERAL_SEQUENTIAL)
                .forPath(path, data.getBytes());
    }

    public void deleteNode(String path) throws Exception {
        if (client.checkExists().forPath(path) != null) {
            client.delete().forPath(path);
        }
    }

    public List<String> getChildren(String path) throws Exception {
        if (client.checkExists().forPath(path) != null) {
            return client.getChildren().forPath(path);
        }
        return null;
    }

    public void addChildListener(String path, Runnable listener) throws Exception {
        // 这里可以利用Curator的PathChildrenCache做监听，示例简化为调用listener.run()
    }

    public void close() {
        client.close();
    }
}
```

---

### 7.5.2 基于Zookeeper实现注册中心 ZookeeperRegistry.java

```java
package org.simple.rpc.registry;

import java.util.ArrayList;
import java.util.List;

public class ZookeeperRegistry implements Registry {

    private static final String ROOT = "/dubbo";
    private final ZkClientWrapper zkClient;

    public ZookeeperRegistry(String zkAddress) {
        this.zkClient = new ZkClientWrapper(zkAddress);
        try {
            zkClient.createPersistentNode(ROOT);
        } catch (Exception e) {
            throw new RegistryException("创建根节点失败", e);
        }
    }

    @Override
    public void register(String serviceName, String serviceAddress) throws RegistryException {
        try {
            String servicePath = ROOT + "/" + serviceName;
            zkClient.createPersistentNode(servicePath);

            // 创建临时顺序节点，表示服务实例
            String addressPath = servicePath + "/address-";
            zkClient.createEphemeralSequentialNode(addressPath, serviceAddress);
            System.out.printf("[ZookeeperRegistry] 注册服务 %s -> %s%n", serviceName, serviceAddress);
        } catch (Exception e) {
            throw new RegistryException("注册服务失败", e);
        }
    }

    @Override
    public void unregister(String serviceName, String serviceAddress) throws RegistryException {
        // ZK临时节点会自动删除，注销可以留空或实现手动删除
        System.out.printf("[ZookeeperRegistry] 注销服务 %s -> %s%n", serviceName, serviceAddress);
    }

    @Override
    public List<String> lookup(String serviceName) throws RegistryException {
        try {
            String servicePath = ROOT + "/" + serviceName;
            List<String> children = zkClient.getChildren(servicePath);
            List<String> addresses = new ArrayList<>();
            if (children != null) {
                for (String child : children) {
                    // child格式如 address-00000001，获取节点数据即服务地址
                    byte[] data = zkClient.getData(servicePath + "/" + child);
                    if (data != null) {
                        addresses.add(new String(data));
                    }
                }
            }
            return addresses;
        } catch (Exception e) {
            throw new RegistryException("查询服务地址失败", e);
        }
    }
}
```

> **说明**: `getData`方法需要补充到`ZkClientWrapper`，示例如下：

```java
public byte[] getData(String path) throws Exception {
    if (client.checkExists().forPath(path) != null) {
        return client.getData().forPath(path);
    }
    return null;
}
```

---

## 7.6 服务端使用示例改造

```java
package org.simple.rpc.provider;

import org.simple.rpc.api.HelloService;
import org.simple.rpc.provider.impl.HelloServiceImpl;
import org.simple.rpc.registry.Registry;
import org.simple.rpc.registry.ZookeeperRegistry;
import org.simple.rpc.core.transport.NettyServer;

public class ProviderServer {

    public static void main(String[] args) throws InterruptedException {
        int port = 8888;
        String serviceAddress = "127.0.0.1:" + port;

        NettyServer server = new NettyServer(port);
        server.registerService(HelloService.class.getName(), new HelloServiceImpl());

        // 使用Zookeeper注册中心
        Registry registry = new ZookeeperRegistry("127.0.0.1:2181");
        registry.register(HelloService.class.getName(), serviceAddress);

        System.out.println("服务提供者启动，监听端口: " + port);
        server.start();
    }
}
```

---

## 7.7 客户端使用示例改造

```java
package org.simple.rpc.consumer;

import org.simple.rpc.api.HelloService;
import org.simple.rpc.core.codec.RpcRequest;
import org.simple.rpc.core.codec.RpcResponse;
import org.simple.rpc.core.transport.NettyClient;
import org.simple.rpc.registry.Registry;
import org.simple.rpc.registry.ZookeeperRegistry;

import java.util.List;

public class ConsumerClient {

    public static void main(String[] args) throws Exception {
        Registry registry = new ZookeeperRegistry("127.0.0.1:2181");

        List<String> addresses = registry.lookup(HelloService.class.getName());
        if (addresses.isEmpty()) {
            System.err.println("服务未注册");
            return;
        }

        String[] hostPort = addresses.get(0).split(":");
        String host = hostPort[0];
        int port = Integer.parseInt(hostPort[1]);

        NettyClient client = new NettyClient(host, port);

        RpcRequest request = new RpcRequest();
        request.setInterfaceName(HelloService.class.getName());
        request.setMethodName("sayHello");
        request.setParamTypes(new Class<?>[]{String.class});
        request.setParameters(new Object[]{"Zookeeper注册中心测试"});

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

## 7.8 小结

* 将注册中心从本地内存升级到Zookeeper，完成分布式注册服务。
* 利用Zookeeper临时顺序节点实现服务实例动态注册和自动失效剔除。
* 为后续实现服务订阅、自动刷新机制奠定基础。
* 代码结构和接口设计保持一致，便于切换注册中心实现。

---
