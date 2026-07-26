package com.axon.dubbo.rpc.cluster.support;

import com.axon.dubbo.rpc.Invoker;
import com.axon.dubbo.rpc.cluster.Cluster;
import com.axon.dubbo.rpc.cluster.Directory;

public class BroadcastCluster implements Cluster {
    public static final String NAME = "broadcast";
    @Override public <T> Invoker<T> join(Directory<T> dir) { return new BroadcastClusterInvoker<>(dir); }
}
