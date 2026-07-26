# 手写 Dubbo 渐进式源码分析方案

## 一、项目概述

本项目参考 [spring-analysis](../spring-analysis/README.md) 和 [mybatis-analysis](../mybatis-analysis/README.md) 的渐进式手写源码模式，从零开始逐步构建一个 Mini-Dubbo，帮助开发者深度理解 Dubbo 的核心架构和设计思想。

### 学习理念

- **渐进式构建**：每一步只添加一个核心概念，代码可独立运行
- **手写核心逻辑**：不依赖 Dubbo 源码，从零实现关键机制
- **包结构对齐官方**：命名和分层与 Apache Dubbo 保持一致
- **每个步骤可测试**：通过单元测试验证每一步的功能

### 项目结构

```
dubbo-analysis/
  pom.xml                          # 父 POM（多模块）
  README.md                        # 项目说明
  dubbo-step-01/                   # 第 1 步：简单 Socket 通信
  dubbo-step-02/                   # 第 2 步：Java 序列化传输
  ...
  dubbo-step-22/                   # 第 22 步：完整架构整合
  Dubbo源码面试题精讲.md             # 配套面试题深度解析
```

### 技术选型

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 8 | 基础语言 |
| Maven | 3.6+ | 构建工具 |
| Netty | 4.1.x | 网络传输（后期引入） |
| ZooKeeper | 3.6.x | 注册中心（后期引入） |
| JUnit | 4.12 | 单元测试 |
| CGLIB / Javassist | 3.3.0 | 动态代理 |

---

## 二、22 步渐进式学习路径

### 全景架构图

```
┌─────────────────────────────────────────────────────────┐
│                    Dubbo 核心架构                         │
├─────────────────────────────────────────────────────────┤
│  Config 配置层    │  ServiceConfig / ReferenceConfig     │
│  Proxy  代理层    │  ProxyFactory / JavassistProxy       │
│  Registry 注册层  │  Registry / ZooKeeper / Nacos        │
│  Cluster 集群层   │  Directory / Router / LoadBalance    │
│  Monitor 监控层   │  MonitorService / MonitorFilter      │
│  Protocol 协议层  │  DubboProtocol / Invoker / Exporter   │
│  Exchange 交换层  │  Request / Response / Future         │
│  Transport 传输层 │  Netty / Mina / Grizzly              │
│  Serialize 序列化 │  Hessian2 / Fastjson / Kryo          │
│  SPI 扩展层       │  ExtensionLoader / Adaptive          │
└─────────────────────────────────────────────────────────┘
```

---

### 第一阶段：RPC 基础通信（Steps 1-5）

> **目标**：理解 RPC 的本质——从 Socket 通信到透明的远程方法调用

---

#### Step 01：简单 Socket 通信

**核心问题**：两台 JVM 之间如何通信？

**新增能力**：
- 服务端监听端口，接收客户端请求
- 客户端通过 Socket 发送数据到服务端
- 服务端返回响应数据

**核心类**（4-6 个）：
```java
com.axon.dubbo.remoting.transport.socket.SimpleServer     // 服务端：ServerSocket 监听
com.axon.dubbo.remoting.transport.socket.SimpleClient     // 客户端：Socket 连接发送
com.axon.dubbo.remoting.transport.socket.Request          // 请求对象
com.axon.dubbo.remoting.transport.socket.Response         // 响应对象
```

**关键知识点**：
- Socket / ServerSocket 基础 API
- BIO 阻塞模型
- 请求-响应 通信模式

**测试验证**：
```java
// 启动服务端 → 客户端发送请求 → 服务端返回响应 → 客户端接收打印
```

---

#### Step 02：Java 原生序列化

**核心问题**：Java 对象如何在网络上传输？

**新增能力**：
- 将 Java 对象转换为字节数组（序列化）
- 将字节数组还原为 Java 对象（反序列化）
- 封装序列化/反序列化工具

**核心类**（3-5 个）：
```java
com.axon.dubbo.common.serialize.Serialization              // 序列化接口
com.axon.dubbo.common.serialize.java.JavaSerialization     // JDK 原生序列化实现
com.axon.dubbo.common.serialize.SerializationFactory       // 序列化工厂
com.axon.dubbo.remoting.transport.socket.ObjectClient      // 改造客户端，支持对象传输
com.axon.dubbo.remoting.transport.socket.ObjectServer      // 改造服务端，支持对象接收
```

**关键知识点**：
- `ObjectOutputStream` / `ObjectInputStream`
- `Serializable` 接口
- `serialVersionUID` 的作用
- 序列化在 RPC 中的角色

---

#### Step 03：动态代理（客户端 Stub）

**核心问题**：如何让远程调用像本地调用一样透明？

**新增能力**：
- 通过 JDK 动态代理拦截方法调用
- 将方法调用转换为网络请求
- 返回结果对调用方透明

**核心类**（5-7 个）：
```java
com.axon.dubbo.rpc.proxy.ProxyFactory                      // 代理工厂接口
com.axon.dubbo.rpc.proxy.jdk.JdkProxyFactory               // JDK 动态代理实现
com.axon.dubbo.rpc.proxy.InvokerInvocationHandler           // InvocationHandler 实现
com.axon.dubbo.rpc.Invocation                              // RPC 调用封装（接口名+方法名+参数）
com.axon.dubbo.rpc.Result                                  // RPC 调用结果封装
com.axon.dubbo.rpc.RpcInvocation                            // RPC 调用实现
```

**关键知识点**：
- `java.lang.reflect.Proxy` / `InvocationHandler`
- RPC 调用元数据封装（接口名、方法名、参数类型、参数值）
- 代理模式在 RPC 中的应用

---

#### Step 04：服务导出与调用（服务端 Skeleton）

**核心问题**：服务端如何接收请求、定位服务、执行方法、返回结果？

**新增能力**：
- 服务端维护服务注册表（接口名 → 实现类实例）
- 根据请求中的接口名和方法名定位具体实现
- 通过反射执行目标方法并返回结果
- 封装完整的 RPC 调用链路

**核心类**（6-8 个）：
```java
com.axon.dubbo.rpc.proxy.skeleton.ServiceSkeleton          // 服务骨架：反射调用本地实现
com.axon.dubbo.common.URL                                   // URL 参数封装（Dubbo 核心数据模型）
com.axon.dubbo.rpc.Exporter                                 // 服务导出器接口
com.axon.dubbo.rpc.protocol.dubbo.DubboExporter             // Dubbo 协议导出器
com.axon.dubbo.rpc.Invoker                                  // 调用器接口（服务端代理调用）
com.axon.dubbo.rpc.protocol.dubbo.DubboInvoker              // Dubbo 协议调用器
com.axon.dubbo.remoting.transport.socket.DispatchServer     // 改造服务端：请求分发
```

**关键知识点**：
- Java 反射：`Method.invoke()`
- 服务注册表的设计（`Map<String, Object>`）
- Invoker 和 Exporter 的概念
- URL 作为 Dubbo 核心数据总线的设计思想

---

#### Step 05：协议抽象

**核心问题**：如何支持多种通信协议？如何将协议层与传输层解耦？

**新增能力**：
- 定义协议接口（export / refer）
- Dubbo 协议的编解码
- 协议头 + 协议体的消息格式
- 将之前步骤的 Socket 通信封装到协议层

**核心类**（8-10 个）：
```java
com.axon.dubbo.rpc.protocol.Protocol                        // 协议接口：export() / refer()
com.axon.dubbo.rpc.protocol.dubbo.DubboProtocol             // Dubbo 协议实现
com.axon.dubbo.rpc.protocol.dubbo.DubboCodec                // Dubbo 编解码器
com.axon.dubbo.rpc.protocol.AbstractProtocol                // 协议抽象基类
com.axon.dubbo.remoting.Codec                               // 编解码接口
com.axon.dubbo.remoting.exchange.ExchangeCodec               // 交换层编解码
com.axon.dubbo.common.Constants                              // 常量定义
```

**Dubbo 协议头格式（16 字节）**：
```
0-1:   魔数 (0xdabb)
2:     序列化标志
3:     请求/响应标志 + 双向/单向标志
4-7:   请求 ID
8-11:  数据长度
12-15: 保留位
```

**关键知识点**：
- 自定义二进制协议设计
- 协议头的作用（魔数校验、序列化方式、请求标识）
- Protocol → Exporter / Invoker 的关系

---

### 第二阶段：注册中心与发现（Steps 6-8）

> **目标**：理解服务治理的核心——注册、发现、订阅

---

#### Step 06：本地服务注册

**核心问题**：如何管理多个服务提供者和消费者？

**新增能力**：
- 服务注册接口定义（register / unregister）
- 本地内存注册中心实现
- 服务 URL 的注册与查询
- 服务端启动时自动注册服务

**核心类**（6-8 个）：
```java
com.axon.dubbo.registry.RegistryService                     // 注册服务接口
com.axon.dubbo.registry.RegistryFactory                     // 注册中心工厂
com.axon.dubbo.registry.support.AbstractRegistry            // 注册中心抽象基类
com.axon.dubbo.registry.support.local.LocalRegistry         // 本地内存注册中心
com.axon.dubbo.registry.support.local.LocalRegistryFactory  // 本地注册中心工厂
com.axon.dubbo.rpc.protocol.RegistryProtocol                // 注册协议包装（先注册再 export）
com.axon.dubbo.rpc.protocol.dubbo.DubboProtocolV2            // 改造：集成注册能力
```

**关键知识点**：
- 注册中心的设计模式（Registry Pattern）
- URL 作为服务元数据的载体
- 服务端注册流程：`export()` → `register()`

---

#### Step 07：服务发现与订阅

**核心问题**：消费者如何动态发现服务提供者？

**新增能力**：
- 服务订阅接口（subscribe / unsubscribe）
- 消费者启动时从注册中心获取服务地址列表
- NotifyListener 回调机制
- 服务地址变更时的动态更新

**核心类**（5-7 个）：
```java
com.axon.dubbo.registry.NotifyListener                      // 通知监听器接口
com.axon.dubbo.registry.support.local.LocalRegistryV2       // 改造：支持订阅
com.axon.dubbo.registry.support.FailbackRegistry            // 失败重试注册中心
com.axon.dubbo.rpc.protocol.dubbo.DubboProtocolV3            // 改造：refer 时先订阅
com.axon.dubbo.rpc.cluster.Directory                        // 服务目录接口
com.axon.dubbo.rpc.cluster.StaticDirectory                  // 静态服务目录
```

**关键知识点**：
- 服务发现流程：`subscribe()` → `notify()` → `Directory`
- 推送 vs 拉取 模式
- provider URL 列表的动态维护

---

#### Step 08：ZooKeeper 注册中心

**核心问题**：如何实现生产级的服务注册与发现？

**新增能力**：
- ZooKeeper 客户端集成（CuratorFramework）
- 基于 ZK 临时节点的服务注册（断连自动清除）
- 基于 ZK Watcher 的服务发现（实时推送变更）
- ZK 路径规范：`/dubbo/接口名/providers/...`

**核心类**（5-7 个）：
```java
com.axon.dubbo.registry.zookeeper.ZookeeperRegistry         // ZK 注册中心实现
com.axon.dubbo.registry.zookeeper.ZookeeperRegistryFactory  // ZK 注册中心工厂
com.axon.dubbo.registry.zookeeper.ZookeeperTransporter      // ZK 传输器
com.axon.dubbo.registry.support.AbstractRegistryV2          // 抽象基类升级（文件缓存）
com.axon.dubbo.common.URLBuilder                             // URL 构建器工具
com.axon.dubbo.common.utils.UrlUtils                         // URL 工具类
```

**ZK 节点结构**：
```
/dubbo
  /com.axon.UserService
    /providers
      /dubbo://192.168.1.100:20880/com.axon.UserService?version=1.0.0    (临时节点)
      /dubbo://192.168.1.101:20880/com.axon.UserService?version=1.0.0
    /consumers
      /consumer://192.168.1.200/com.axon.UserService?application=app1
    /configurators
    /routers
```

**关键知识点**：
- ZooKeeper 数据模型（ZNode、临时节点、持久节点）
- Watcher 机制在服务发现中的应用
- Session 超时与重连处理
- 本地文件缓存（容灾设计）

---

### 第三阶段：集群容错与负载均衡（Steps 9-12）

> **目标**：理解 Dubbo 的集群治理能力——多提供者下的调用策略

---

#### Step 09：多提供者与服务目录

**核心问题**：存在多个提供者时，消费者如何管理和选择？

**新增能力**：
- 服务目录（Directory）从注册中心获取多提供者列表
- Directory 将提供者 URL 列表转换为 Invoker 列表
- RegistryDirectory 监听注册中心变更动态刷新

**核心类**（6-8 个）：
```java
com.axon.dubbo.rpc.cluster.Directory                        // 服务目录接口（升级）
com.axon.dubbo.rpc.cluster.directory.RegistryDirectory      // 注册中心目录（监听 ZK 变更）
com.axon.dubbo.rpc.cluster.directory.StaticDirectory        // 静态目录（手动配置）
com.axon.dubbo.rpc.cluster.support.AbstractDirectory        // 目录抽象基类
com.axon.dubbo.rpc.Invoker                                  // Invoker 接口（升级）
com.axon.dubbo.rpc.protocol.dubbo.DubboInvoker              // Dubbo Invoker（升级：多地址）
com.axon.dubbo.rpc.RpcStatus                                 // RPC 调用状态
```

**关键知识点**：
- Directory → Invoker 列表的转换逻辑
- RegistryDirectory 的 notify 回调处理
- Invoker 的封装层次（Protocol → Directory → Cluster → Proxy）

---

#### Step 10：负载均衡策略

**核心问题**：如何从多个提供者中选择一个进行调用？

**新增能力**：
- LoadBalance 接口定义
- 四种经典负载均衡策略实现
- 权重计算和随机选择算法

**核心类**（7-9 个）：
```java
com.axon.dubbo.rpc.cluster.LoadBalance                      // 负载均衡接口
com.axon.dubbo.rpc.cluster.loadbalance.RandomLoadBalance    // 随机（带权重）
com.axon.dubbo.rpc.cluster.loadbalance.RoundRobinLoadBalance // 轮询
com.axon.dubbo.rpc.cluster.loadbalance.LeastActiveLoadBalance // 最少活跃调用数
com.axon.dubbo.rpc.cluster.loadbalance.ConsistentHashLoadBalance // 一致性哈希
com.axon.dubbo.rpc.cluster.support.AbstractLoadBalance      // 负载均衡抽象基类
com.axon.dubbo.rpc.cluster.loadbalance.WeightRandom          // 权重随机算法
```

**算法要点**：
- **随机权重**：累积权重 → 随机偏移 → 二分查找
- **轮询**：`(current + 1) % total` + 权重平滑轮询
- **最少活跃**：过滤活跃数最少的 + 随机权重
- **一致性哈希**：`TreeMap` 构建哈希环 + `IdenticalHash` 参数一致性

**关键知识点**：
- 权重是如何从 URL 中读取的（`weight` 参数 / 预热权重）
- 一致性哈希如何保证相同参数总是路由到同一节点
- LoadBalance 的扩展机制设计

---

#### Step 11：集群调用器

**核心问题**：如何将多个提供者封装为一个集群 Invoker？

**新增能力**：
- Cluster 接口：将 Directory 中的多个 Invoker 合并为一个
- 集群 Invoker 负责调用路由、负载均衡、容错
- FailoverClusterInvoker（失败自动切换）

**核心类**（6-8 个）：
```java
com.axon.dubbo.rpc.cluster.Cluster                          // 集群接口：join(Directory)
com.axon.dubbo.rpc.cluster.support.AbstractClusterInvoker   // 集群 Invoker 抽象基类
com.axon.dubbo.rpc.cluster.support.FailoverClusterInvoker   // 失败自动切换集群
com.axon.dubbo.rpc.cluster.support.FailoverCluster          // Failover 集群实现
com.axon.dubbo.rpc.cluster.support.ClusterUtils             // 集群工具类
com.axon.dubbo.rpc.cluster.wrapper.ClusterWrapper           // 集群包装器
```

**调用链路**：
```
Proxy -> ClusterInvoker -> Directory.list() -> Router -> LoadBalance -> Invoker -> Protocol -> Transport
```

**关键知识点**：
- Cluster 的工厂模式创建
- AbstractClusterInvoker 的模板方法设计
- Invoker 引用链的组装：`protocol.refer()` → `cluster.join(directory)`

---

#### Step 12：容错策略全集

**核心问题**：调用失败时如何处理？

**新增能力**：
- 六种容错策略实现
- 重试次数、超时、异常处理
- Mock 降级支持

**核心类**（8-10 个）：
```java
com.axon.dubbo.rpc.cluster.support.FailfastClusterInvoker   // 快速失败（只调用一次）
com.axon.dubbo.rpc.cluster.support.FailsafeClusterInvoker   // 安全失败（忽略异常）
com.axon.dubbo.rpc.cluster.support.FailbackClusterInvoker   // 失败自动恢复（后台重试）
com.axon.dubbo.rpc.cluster.support.ForkingClusterInvoker    // 并行调用（多个并发，取第一个成功）
com.axon.dubbo.rpc.cluster.support.BroadcastClusterInvoker  // 广播调用（逐个调用所有提供者）
com.axon.dubbo.rpc.cluster.support.AvailableClusterInvoker  // 可用性检查
com.axon.dubbo.rpc.cluster.merger.Merger                     // 结果合并器接口
com.axon.dubbo.rpc.cluster.merger.MergerFactory              // 结果合并器工厂
com.axon.dubbo.rpc.cluster.configurator.Configurator         // 配置覆盖器
```

**容错策略对比**：

| 策略 | 适用场景 | 实现要点 |
|------|---------|---------|
| Failover | 读操作（幂等） | 重试 n 次，切换其他提供者 |
| Failfast | 写操作（非幂等） | 只调一次，立即报错 |
| Failsafe | 日志记录 | 吞掉异常，记录日志 |
| Failback | 消息通知 | 失败后异步重试 |
| Forking | 实时性高 | 并发调用多个，取最快 |
| Broadcast | 通知所有 | 逐个调用，任一失败即失败 |

**关键知识点**：
- 容错策略的选择原则（幂等性 → 可重试）
- `retries` 配置的含义
- Cluster + LoadBalance + Router 的协作

---

### 第四阶段：框架基础设施（Steps 13-17）

> **目标**：理解 Dubbo 的扩展机制和内部架构

---

#### Step 13：SPI 扩展机制（核心精讲）

**核心问题**：Dubbo 如何实现"微内核 + 插件化"架构？

**新增能力**：
- SPI 注解定义
- ExtensionLoader 扩展点加载器
- 自适应扩展（Adaptive）
- 扩展点包装（Wrapper）
- 扩展点激活（Activate）

**核心类**（8-12 个）：
```java
com.axon.dubbo.common.extension.SPI                         // SPI 注解
com.axon.dubbo.common.extension.Adaptive                     // 自适应注解
com.axon.dubbo.common.extension.Activate                      // 激活注解
com.axon.dubbo.common.extension.ExtensionLoader              // 扩展点加载器（核心）
com.axon.dubbo.common.extension.ExtensionFactory             // 扩展工厂
com.axon.dubbo.common.extension.AdaptiveExtensionFactory     // 自适应扩展工厂
com.axon.dubbo.common.extension.SpiExtensionFactory          // SPI 扩展工厂
com.axon.dubbo.common.extension.Wrapper                      // 包装器标记
```

**Dubbo SPI vs JDK SPI 对比**：
| 特性 | JDK SPI | Dubbo SPI |
|------|---------|-----------|
| 加载方式 | 全量加载 | 按需加载（按名称） |
| 默认值 | 不支持 | `@SPI("default")` |
| AOP | 不支持 | Wrapper 自动包装 |
| IOC | 不支持 | `set` 注入 |
| 自适应 | 不支持 | `@Adaptive` 动态生成 |

**关键知识点**：
- `ExtensionLoader.getExtensionLoader()` 的设计
- `META-INF/dubbo/internal/` 配置文件规范
- `@Adaptive` 动态编译生成代理类的原理
- Wrapper 类的责任链包装机制
- IOC 注入的 `injectExtension()` 实现

> **本步骤是整个系列中最重要的一步，建议花 2-3 倍时间深入理解！**

---

#### Step 14：过滤器链（调用拦截）

**核心问题**：如何在 RPC 调用的各个环节插入自定义逻辑？

**新增能力**：
- Filter 过滤器接口
- Provider 端过滤器链
- Consumer 端过滤器链
- 内置过滤器实现

**核心类**（8-10 个）：
```java
com.axon.dubbo.rpc.Filter                                   // 过滤器接口（@SPI）
com.axon.dubbo.rpc.filter.ProviderFilter                     // Provider 过滤器基类
com.axon.dubbo.rpc.filter.ConsumerFilter                     // Consumer 过滤器基类
com.axon.dubbo.rpc.filter.AccessLogFilter                    // 访问日志过滤器
com.axon.dubbo.rpc.filter.ExceptionFilter                    // 异常处理过滤器
com.axon.dubbo.rpc.filter.TimeoutFilter                      // 超时过滤器
com.axon.dubbo.rpc.filter.TokenFilter                        // Token 鉴权过滤器
com.axon.dubbo.rpc.filter.ActiveLimitFilter                  // 并发限制过滤器
com.axon.dubbo.rpc.protocol.ProtocolFilterWrapper            // 协议过滤器包装
com.axon.dubbo.rpc.protocol.FilterChainBuilder                // 过滤器链构建器
```

**过滤器链调用模型**：
```
Consumer: 请求 → [Consumer Filters] → Invoker.invoke()
Provider: 请求 → [Provider Filters] → Invoker.invoke() → 实际服务
```

**关键知识点**：
- 过滤器链的构建（`List<Filter>` + 匿名 Invoker 构建调用链）
- `@Activate` 注解的条件激活（group、order、value）
- 特殊过滤器：`MonitorFilter`、`TpsLimitFilter`、`ExecuteLimitFilter`

---

#### Step 15：消费者端过滤器链（深入）

**核心问题**：消费者端如何通过过滤器实现更多功能？

**新增能力**：
- 消费端 Context 传递（隐式参数）
- 泛化调用过滤器
- 结果缓存过滤器
- Consumer 端过滤器完整实现

**核心类**（5-7 个）：
```java
com.axon.dubbo.rpc.filter.ContextFilter                     // 上下文过滤器（隐式传参）
com.axon.dubbo.rpc.filter.CacheFilter                        // 结果缓存过滤器
com.axon.dubbo.rpc.filter.GenericFilter                      // 泛化调用过滤器
com.axon.dubbo.rpc.filter.FutureFilter                       // 异步调用过滤器
com.axon.dubbo.rpc.RpcContext                                // RPC 上下文
com.axon.dubbo.rpc.support.RpcUtils                           // RPC 工具类
```

**RpcContext 隐式传参原理**：
```java
// Consumer 端设置
RpcContext.getContext().setAttachment("userId", "12345");
// Provider 端获取
String userId = RpcContext.getContext().getAttachment("userId");
```

**关键知识点**：
- `RpcContext` 的 ThreadLocal 设计
- 隐式参数如何通过协议传输
- 异步调用的 Future 模式

---

#### Step 16：Netty 传输层

**核心问题**：如何用 Netty 替换 BIO Socket，实现高性能网络通信？

**新增能力**：
- Transport 层抽象（Transporter 接口）
- Netty Server / Client 实现
- Channel 和 ChannelHandler
- 编码器 / 解码器
- 心跳机制

**核心类**（10-15 个）：
```java
com.axon.dubbo.remoting.Transporter                          // 传输器接口（SPI）
com.axon.dubbo.remoting.transport.netty.NettyTransporter     // Netty 传输器
com.axon.dubbo.remoting.transport.netty.NettyServer          // Netty 服务端
com.axon.dubbo.remoting.transport.netty.NettyClient          // Netty 客户端
com.axon.dubbo.remoting.transport.netty.NettyChannel         // Netty Channel 封装
com.axon.dubbo.remoting.transport.netty.NettyCodecAdapter    // 编解码适配器
com.axon.dubbo.remoting.Channel                              // Channel 抽象接口
com.axon.dubbo.remoting.ChannelHandler                       // Channel 处理器接口
com.axon.dubbo.remoting.transport.AbstractServer             // Server 抽象基类
com.axon.dubbo.remoting.transport.AbstractClient             // Client 抽象基类
com.axon.dubbo.remoting.transport.netty.NettyChannelHandler  // Netty Handler 适配
com.axon.dubbo.remoting.exchange.header.HeartbeatHandler      // 心跳处理器
```

**Netty Pipeline 结构**：
```
┌────────────────────────────┐
│   Netty TCP Pipeline       │
├────────────────────────────┤
│   FrameDecoder (粘包拆包)   │
│   Encoder (协议编码)        │
│   Decoder (协议解码)        │
│   NettyServerHandler        │
│   HeartbeatHandler          │
└────────────────────────────┘
```

**关键知识点**：
- Netty 的 Reactor 线程模型（Boss/Worker）
- Channel 抽象封装（适配不同 NIO 框架）
- 粘包/拆包问题与 Dubbo 的解决方案
- 长连接的心跳保活机制

---

#### Step 17：交换层（Exchange Layer）

**核心问题**：如何在异步网络传输上实现同步请求-响应模型？

**新增能力**：
- Exchange 层抽象
- 请求-响应映射（通过 Request ID）
- 同步转异步（Future 模式）
- 超时处理

**核心类**（8-12 个）：
```java
com.axon.dubbo.remoting.exchange.ExchangeServer              // 交换层服务端
com.axon.dubbo.remoting.exchange.ExchangeClient              // 交换层客户端
com.axon.dubbo.remoting.exchange.ExchangeChannel             // 交换层通道
com.axon.dubbo.remoting.exchange.header.HeaderExchangeServer // Dubbo 交换层服务端实现
com.axon.dubbo.remoting.exchange.header.HeaderExchangeClient // Dubbo 交换层客户端实现
com.axon.dubbo.remoting.exchange.header.HeaderExchangeChannel // Dubbo 交换层通道实现
com.axon.dubbo.remoting.exchange.ResponseFuture               // 响应 Future
com.axon.dubbo.remoting.exchange.support.DefaultFuture        // 默认 Future（核心）
com.axon.dubbo.remoting.exchange.support.MultiMessage          // 多消息支持
com.axon.dubbo.remoting.exchange.Request                      // 交换层请求（升级）
com.axon.dubbo.remoting.exchange.Response                     // 交换层响应（升级）
```

**DefaultFuture 设计（核心）**：
```java
// DefaultFuture 内部维护全局 Map
private static final Map<Long, DefaultFuture> FUTURES = new ConcurrentHashMap<>();

// 发送请求时
DefaultFuture future = new DefaultFuture(channel, request);
FUTURES.put(request.getId(), future);
return future;

// 接收响应时
DefaultFuture future = FUTURES.remove(response.getId());
future.received(response);
```

**关键知识点**：
- Request ID 如何关联请求和响应
- `DefaultFuture.get(timeout)` 的超时实现
- 同步调用 = `future.get()` 阻塞等待
- 异步调用 = 设置 `ResponseCallback`
- Exchange 层在整体架构中的位置

---

### 第五阶段：高级特性（Steps 18-22）

> **目标**：掌握生产级 RPC 框架的完整能力

---

#### Step 18：序列化扩展

**核心问题**：如何支持多种序列化方式，并按需切换？

**新增能力**：
- 序列化 SPI 扩展
- Hessian2 序列化实现
- Fastjson 序列化实现
- Kryo 序列化实现
- 序列化性能对比

**核心类**（6-8 个）：
```java
com.axon.dubbo.common.serialize.Serialization                // 序列化接口（升级为 SPI）
com.axon.dubbo.common.serialize.hessian2.Hessian2Serialization // Hessian2 实现
com.axon.dubbo.common.serialize.fastjson.FastJsonSerialization // Fastjson 实现
com.axon.dubbo.common.serialize.kryo.KryoSerialization       // Kryo 实现
com.axon.dubbo.common.serialize.support.SerializationOptimizer // 序列化优化器
com.axon.dubbo.common.serialize.ObjectInput                  // 反序列化抽象输入
com.axon.dubbo.common.serialize.ObjectOutput                 // 序列化抽象输出
```

**序列化方式对比**：

| 方式 | 大小 | 速度 | 跨语言 | 适用场景 |
|------|------|------|--------|---------|
| Hessian2 | 小 | 快 | 是 | 默认选择 |
| Fastjson | 中 | 快 | 否 | 文本可读 |
| Kryo | 很小 | 很快 | 否 | 高性能 |
| JDK | 大 | 慢 | 否 | 兼容 |

**关键知识点**：
- 序列化标志位如何决定使用哪种序列化
- Hessian2 的 `SerializerFactory` 对象缓存
- Kryo 的线程不安全问题与 `ThreadLocal` 解决方案

---

#### Step 19：监控中心

**核心问题**：如何收集和展示 RPC 调用数据？

**新增能力**：
- MonitorService 接口
- MonitorFilter（调用统计过滤器）
- 本地内存统计
- 调用次数、耗时、成功/失败统计

**核心类**（5-7 个）：
```java
com.axon.dubbo.monitor.MonitorService                        // 监控服务接口
com.axon.dubbo.monitor.MonitorFilter                         // 监控过滤器
com.axon.dubbo.monitor.support.AbstractMonitorFactory        // 监控工厂抽象
com.axon.dubbo.monitor.support.MonitorFilterListener         // 监控过滤器监听器
com.axon.dubbo.monitor.dubbo.DubboMonitorFactory             // Dubbo Monitor 工厂
com.axon.dubbo.common.status.StatusChecker                    // 状态检查接口
com.axon.dubbo.common.status.support.StatusUtils              // 状态工具
```

**监控数据模型**：
```
调用次数 | 成功次数 | 失败次数 | 平均耗时 | 最大耗时 | 最小耗时 | 并发数
```

**关键知识点**：
- `MonitorFilter` 在过滤器链中的位置
- 监控数据的采集与聚合策略
- 定时上报 vs 实时上报

---

#### Step 20：配置层

**核心问题**：如何统一管理 Dubbo 的配置？如何支持多种配置方式？

**新增能力**：
- 配置抽象（ServiceConfig / ReferenceConfig）
- XML 配置解析
- 属性配置（properties）
- API 配置（编程方式）
- 注解配置

**核心类**（10-15 个）：
```java
com.axon.dubbo.config.ServiceConfig                          // 服务提供者配置
com.axon.dubbo.config.ReferenceConfig                         // 服务消费者配置
com.axon.dubbo.config.ProtocolConfig                          // 协议配置
com.axon.dubbo.config.RegistryConfig                          // 注册中心配置
com.axon.dubbo.config.ApplicationConfig                      // 应用配置
com.axon.dubbo.config.ModuleConfig                            // 模块配置
com.axon.dubbo.config.MonitorConfig                           // 监控配置
com.axon.dubbo.config.ProviderConfig                          // Provider 默认配置
com.axon.dubbo.config.ConsumerConfig                          // Consumer 默认配置
com.axon.dubbo.config.MethodConfig                            // 方法级别配置
com.axon.dubbo.config.ArgumentConfig                          // 参数级别配置
com.axon.dubbo.config.spring.ServiceBean                      // Spring 集成 Bean
com.axon.dubbo.config.spring.ReferenceBean                    // Spring 集成 Bean
com.axon.dubbo.config.spring.schema.DubboNamespaceHandler     // Spring XML 命名空间
```

**配置优先级（从高到低）**：
```
方法级 > 接口级 > 消费者/提供者级 > 全局级
-D JVM 参数 > XML > properties
```

**关键知识点**：
- 配置覆盖规则
- `-D` 参数 → XML → 默认值的优先级链
- ServiceConfig.export() 的完整流程
- ReferenceConfig.get() 的完整流程

---

#### Step 21：动态配置中心

**核心问题**：如何实现运行时不重启的配置变更？

**新增能力**：
- ConfigCenter 接口
- ZooKeeper 作为配置中心
- 配置优先级：配置中心 > 本地配置
- 配置变更监听

**核心类**（5-7 个）：
```java
com.axon.dubbo.configcenter.ConfigChangeEvent                // 配置变更事件
com.axon.dubbo.configcenter.ConfigChangeListener             // 配置变更监听器
com.axon.dubbo.configcenter.DynamicConfiguration              // 动态配置接口
com.axon.dubbo.configcenter.support.zookeeper.ZookeeperDynamicConfiguration // ZK 动态配置
com.axon.dubbo.configcenter.support.AbstractDynamicConfiguration // 动态配置抽象
com.axon.dubbo.config.AbstractConfig                          // 配置基类（升级）
```

**关键知识点**：
- 配置中心的架构设计
- ZK 节点的 Watch 机制二次利用
- 配置变更后的服务治理策略（Override / 路由规则调整）

---

#### Step 22：完整架构整合与设计模式总结

**核心问题**：如何从全局视角理解 Dubbo 的架构？

**新增能力**：
- 所有模块的完整集成
- 端到端调用链路测试
- Dubbo 中所有设计模式的总结
- 面试高频问题梳理

**核心类**（3-5 个）：
```java
com.axon.dubbo.common.utils.DubboApp                           // 一站式启动工具
com.axon.dubbo.demo.provider.DemoProvider                       // 完整 Provider 示例
com.axon.dubbo.demo.consumer.DemoConsumer                       // 完整 Consumer 示例
```

**Dubbo 设计模式全景**：

| 设计模式 | 在 Dubbo 中的应用 |
|---------|------------------|
| **工厂模式** | ProxyFactory、RegistryFactory、ExtensionFactory |
| **单例模式** | ExtensionLoader 实例管理 |
| **代理模式** | 客户端 Stub（JDK/Javassist Proxy） |
| **模板方法** | AbstractRegistry、AbstractProtocol、AbstractClusterInvoker |
| **责任链模式** | Filter Chain |
| **观察者模式** | NotifyListener（注册中心变更） |
| **装饰器模式** | ProtocolFilterWrapper、ProtocolListenerWrapper |
| **适配器模式** | Transporter → NettyTransporter / MinaTransporter |
| **策略模式** | LoadBalance（Random / RoundRobin / LeastActive） |
| **外观模式** | DubboBootstrap 一站式启动 |
| **建造者模式** | URLBuilder、ServiceConfig |
| **微内核模式** | SPI 扩展加载机制 |

**完整调用链路图**：
```
Consumer 端                                      Provider 端
────────────────────────────────────────────────────────────────

1. ProxyFactory.getProxy(interface)
   └── InvokerInvocationHandler
       └── ClusterInvoker.invoke()
           ├── Directory.list()   ← 从注册中心获取 Invoker 列表
           ├── Router.route()     ← 路由过滤
           ├── LoadBalance.select() ← 负载均衡选一个
           └── Invoker.invoke()
               ├── [Consumer Filter Chain]
               └── Protocol.refer()
                   └── ExchangeClient.request()
                       └── NettyClient.send()
                            │
                          网络传输  ──────────────────────→
                                                          │
                                              NettyServer.received()
                                                  └── ExchangeServer
                                                      └── Protocol.export()
                                                          ├── [Provider Filter Chain]
                                                          └── Invoker.invoke()
                                                              └── ServiceSkeleton.invoke()
                                                                  └── Method.invoke()
```

---

## 三、配套文档体系

### 3.1 每步标准文档

每个 `dubbo-step-NN/` 目录下包含：

```
dubbo-step-NN/
  pom.xml                          # Maven 子模块
  stepNN-XXX.md                    # 步骤详解文档
  src/
    main/java/com/axon/dubbo/...   # 手写 Dubbo 源码
    test/java/com/axon/dubbo/...   # 测试用例
```

**步骤文档模板**（stepNN-XXX.md）：

```markdown
# Step NN：XXX

## 一、本步骤解决的问题
## 二、新增了哪些能力
## 三、核心类一览
## 四、核心原理剖析（关键代码+图解）
## 五、面试常见问法
## 六、快速复习入口（文件路径列表）
```

### 3.2 面试题精讲（独立文档）

`Dubbo源码面试题精讲.md` 涵盖以下主题：

| 编号 | 问题 | 对应步骤 |
|------|------|---------|
| 1 | 说说 Dubbo 的整体架构？ | Step 22 |
| 2 | Dubbo 的 SPI 机制和 JDK SPI 有什么区别？ | Step 13 |
| 3 | Dubbo 服务暴露的完整流程？ | Step 04, 06, 20 |
| 4 | Dubbo 服务引用的完整流程？ | Step 03, 07, 20 |
| 5 | Dubbo 支持哪些负载均衡策略？原理是什么？ | Step 10 |
| 6 | Dubbo 的集群容错策略有哪些？分别适用什么场景？ | Step 12 |
| 7 | Dubbo 的协议设计（Dubbo 协议头）？ | Step 05 |
| 8 | Dubbo 的 RPC 调用链路是怎样的？ | Step 17, 22 |
| 9 | Dubbo 的 Filter 机制如何工作？ | Step 14, 15 |
| 10 | Dubbo 如何实现服务注册与发现？ | Step 06, 07, 08 |
| 11 | Dubbo 的服务目录（Directory）是什么？ | Step 09 |
| 12 | Dubbo 如何实现异步调用？ | Step 17 |
| 13 | Dubbo 的隐式参数传递是如何实现的？ | Step 15 |
| 14 | Dubbo 的 Netty 通信模型是怎样的？ | Step 16 |
| 15 | Dubbo 的序列化方式有哪些？如何选择？ | Step 02, 18 |
| 16 | Dubbo 如何实现动态代理？ | Step 03, 22 |
| 17 | Dubbo 中 URL 的作用是什么？ | Step 04 |
| 18 | Dubbo 的泛化调用是如何实现的？ | Step 15 |
| 19 | Dubbo 的线程模型是怎样的？ | Step 16 |
| 20 | Dubbo 如何实现服务降级和 Mock？ | Step 12 |
| 21 | Dubbo 如何实现配置的动态覆盖？ | Step 21 |
| 22 | Dubbo 中用了哪些设计模式？ | Step 22 |
| 23 | Dubbo 的服务端如何实现请求分发？ | Step 04, 17 |
| 24 | Dubbo 如何处理粘包和拆包？ | Step 05, 16 |
| 25 | Dubbo 的 ExtensionLoader 是如何实现 IOC 和 AOP 的？ | Step 13 |

---

## 四、核心设计决策

### 4.1 为什么从 Socket 开始而不是直接用 Netty？

1. **可见性**：Socket API 是 Java 标准库，每个开发者都熟悉，不需要额外学习 Netty
2. **可理解性**：BIO 模型的请求-响应逻辑清晰直观，Netty 的事件驱动模型增加了理解难度
3. **渐进原则**：先理解"是什么"（RPC 的本质是 Socket + 序列化），再理解"怎么优化"（用 Netty 替代）

### 4.2 为什么 Step 13（SPI）放在中间而不是开头？

1. **前置理解**：SPI 是 Dubbo 的"骨架"，但理解它需要先理解 Protocol、Registry、LoadBalance 等"血肉"
2. **有需求才需要**：没有前面的步骤，开发者无法理解"为什么要发明一个比 JDK SPI 更复杂的机制"
3. **重点突破**：Step 13 是整个系列中最重要的一步，放在中间可以让开发者在前面的基础上深入学习

### 4.3 包结构与官方 Dubbo 的对齐

| 官方 Dubbo 包 | 本项目包 | 说明 |
|--------------|---------|------|
| `org.apache.dubbo.common` | `com.axon.dubbo.common` | 通用工具和扩展 |
| `org.apache.dubbo.rpc` | `com.axon.dubbo.rpc` | RPC 核心抽象 |
| `org.apache.dubbo.remoting` | `com.axon.dubbo.remoting` | 远程通信抽象 |
| `org.apache.dubbo.registry` | `com.axon.dubbo.registry` | 注册中心 |
| `org.apache.dubbo.cluster` | `com.axon.dubbo.cluster` | 集群容错 |
| `org.apache.dubbo.monitor` | `com.axon.dubbo.monitor` | 监控 |
| `org.apache.dubbo.config` | `com.axon.dubbo.config` | 配置 |

---

## 五、推荐的阅读路径

### 路径 A：快速入门（理解 RPC 本质）
```
Step 01 → 02 → 03 → 04 → 05
```
5 步即可理解一个最小 RPC 框架的完整通信链路。

### 路径 B：架构理解（掌握 Dubbo 核心设计）
```
Step 01-05 → Step 13（SPI重点）→ Step 06-08 → Step 09-12 → Step 16-17
```
适合想快速理解 Dubbo 架构设计的开发者。

### 路径 C：完整深入（面试 + 源码级理解）
```
Step 01 → ... → Step 22（按顺序全部完成）
```
适合准备面试或需要深度掌握 Dubbo 的开发者。

### 路径 D：按需查阅（遇到问题看对应步骤）
- 想知道负载均衡怎么实现？→ Step 10
- 想知道 SPI 怎么做的？→ Step 13
- 想知道 Netty 怎么集成的？→ Step 16
- 想知道设计模式？→ Step 22

---

## 六、与 Spring/MyBatis 项目的对比

| 维度 | Spring 项目 | MyBatis 项目 | Dubbo 项目 |
|------|------------|-------------|-----------|
| 核心概念 | IoC/DI、AOP、事务 | SQL 映射、缓存、插件 | RPC、注册中心、集群 |
| 入门步骤 | Bean 容器（Step 01） | SQL 会话（Step 01） | Socket 通信（Step 01） |
| 核心难点 | 循环依赖三级缓存 | 动态 SQL 解析 | SPI 扩展机制 |
| 设计模式重点 | 模板方法、策略 | 代理、组合 | 代理、责任链、SPI |
| 步骤数 | 21 | 22 | 22 |
| 网络通信 | 无（纯 JVM 内） | JDBC 网络层隐藏 | 核心关注点（Netty） |

三者共同的学习路径规律：
1. **先理解"做什么"** → 从最简单的可运行代码开始
2. **再理解"怎么做"** → 逐层添加能力，每一步聚焦一个概念
3. **最后理解"为什么"** → 结合设计模式、架构设计理解设计者的意图

---

## 七、开发计划

| 阶段 | 步骤 | 预计工作量 | 核心交付 |
|------|------|----------|---------|
| 第一阶段 | Step 01-05 | 5-7 天 | 可运行的 Mini-RPC |
| 第二阶段 | Step 06-08 | 4-5 天 | 服务注册与发现 |
| 第三阶段 | Step 09-12 | 5-7 天 | 集群治理能力 |
| 第四阶段 | Step 13-17 | 7-10 天 | 框架基础设施（SPI 重点） |
| 第五阶段 | Step 18-22 | 5-7 天 | 高级特性和整合 |
| 配套文档 | 面试题精讲 | 3-5 天 | 25 道面试题深度解析 |
| **总计** | **22 步** | **约 30-40 天** | 完整 Mini-Dubbo + 面试体系 |

---

## 八、下一步

请确认此方案后，我们将按照以下顺序推进：

1. **创建项目骨架**：`pom.xml` + 22 个 `dubbo-step-NN` 模块
2. **Step 01 开发**：简单 Socket 通信 → 验证可行性
3. **逐步推进**：按计划完成每一步的代码 + 文档
4. **面试题编写**：边写代码边整理面试问题
5. **整合验收**：Step 22 全链路测试 + 性能验证
