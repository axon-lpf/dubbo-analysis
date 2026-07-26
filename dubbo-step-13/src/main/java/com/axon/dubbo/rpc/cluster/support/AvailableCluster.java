package com.axon.dubbo.rpc.cluster.support;

import com.axon.dubbo.rpc.Invoker;
import com.axon.dubbo.rpc.cluster.Cluster;
import com.axon.dubbo.rpc.cluster.Directory;

public class AvailableCluster implements Cluster {
    public static final String NAME = "available";
    @Override public <T> Invoker<T> join(Directory<T> dir) { return new AvailableClusterInvoker<>(dir); }
}
