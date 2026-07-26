package com.axon.dubbo.rpc.cluster.support;

import com.axon.dubbo.rpc.Invoker;
import com.axon.dubbo.rpc.cluster.Cluster;
import com.axon.dubbo.rpc.cluster.Directory;

/**
 * Failover 集群实现
 *
 * 创建 FailoverClusterInvoker：
 * - 内置 LoadBalance（加权随机）
 * - 失败自动切换下一个 Provider
 * - 最多重试 retries 次
 *
 * 对应官方源码：org.apache.dubbo.rpc.cluster.support.FailoverCluster
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class FailoverCluster implements Cluster {

    /**
     * 集群名称
     */
    public static final String NAME = "failover";

    @Override
    public <T> Invoker<T> join(Directory<T> directory) {
        return new FailoverClusterInvoker<>(directory);
    }
}
