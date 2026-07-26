# Dubbo 渐进式源码分析

本项目参考 [spring-analysis](../spring-analysis/README.md) 和 [mybatis-analysis](../mybatis-analysis/README.md) 的渐进式手写源码模式，从零开始逐步构建一个 Mini-Dubbo，帮助开发者深度理解 Dubbo 的核心架构和设计思想。

## 学习路径

| 阶段 | 步骤 | 主题 |
|------|------|------|
| **RPC 基础通信** | [Step 01](dubbo-step-01/step01-简单Socket通信.md) | 简单 Socket 通信 |
| | Step 02 | Java 序列化传输 |
| | Step 03 | 动态代理（客户端 Stub） |
| | Step 04 | 服务导出与反射调用（服务端 Skeleton） |
| | Step 05 | 协议抽象与编解码 |
| **注册与发现** | Step 06 | 本地服务注册 |
| | Step 07 | 服务发现与订阅 |
| | Step 08 | ZooKeeper 注册中心 |
| **集群与容错** | Step 09 | 多提供者与服务目录 |
| | Step 10 | 负载均衡策略 |
| | Step 11 | 集群调用器 |
| | Step 12 | 容错策略全集 |
| **基础设施** | Step 13 | SPI 扩展机制（核心精讲） |
| | Step 14 | 过滤器链（Provider） |
| | Step 15 | 过滤器链（Consumer） |
| | Step 16 | Netty 传输层 |
| | Step 17 | 交换层（Exchange Layer） |
| **高级特性** | Step 18 | 序列化扩展 |
| | Step 19 | 监控中心 |
| | Step 20 | 配置层 |
| | Step 21 | 动态配置中心 |
| | Step 22 | 完整架构整合与设计模式 |

## 快速开始

```bash
# 编译全部模块
mvn clean compile

# 运行 Step 01 测试
cd dubbo-step-01
mvn test
```

## 配套文档

- [DUBBO渐进式源码分析方案](DUBBO渐进式源码分析方案.md) —— 完整的方案设计
- [Dubbo源码面试题精讲](Dubbo源码面试题精讲.md) —— 配套面试题（待完成）
