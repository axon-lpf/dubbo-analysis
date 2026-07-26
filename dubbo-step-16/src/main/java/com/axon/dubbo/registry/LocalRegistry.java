package com.axon.dubbo.registry;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.registry.support.AbstractRegistry;

/**
 * 本地内存注册中心
 *
 * 基于内存 Map 的注册中心实现，所有数据存储在 JVM 内存中。
 *
 * 优点：
 * - 零依赖，无需安装外部服务
 * - 启动快，适合开发测试
 *
 * 局限：
 * - 数据不持久化，重启丢失
 * - 仅限单 JVM 内使用（Provider 和 Consumer 必须在同一 JVM）
 * - 不支持跨机器通信
 *
 * 在 Step 07-08 中，我们将引入支持跨 JVM 的注册中心：
 * - Step 07: 服务发现与订阅（独立的 registry 进程之间通信）
 * - Step 08: ZooKeeper 注册中心实现
 *
 * 对应官方源码：没有直接对应（Dubbo 没有本地注册中心），
 * 但架构上类似 RedisRegistry / ZookeeperRegistry 的简化版。
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class LocalRegistry extends AbstractRegistry {

    public LocalRegistry(URL registryUrl) {
        super(registryUrl);
        System.out.println("[LocalRegistry] 本地注册中心已启动");
    }
}
