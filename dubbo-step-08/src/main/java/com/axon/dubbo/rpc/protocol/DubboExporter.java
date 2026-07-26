package com.axon.dubbo.rpc.protocol;

import com.axon.dubbo.rpc.Exporter;
import com.axon.dubbo.rpc.Invoker;

public class DubboExporter<T> implements Exporter<T> {
    private final Invoker<T> invoker;
    private volatile boolean unexported = false;

    public DubboExporter(Invoker<T> invoker) { this.invoker = invoker; }

    @Override public Invoker<T> getInvoker() { return invoker; }

    @Override
    public void unexport() {
        if (unexported) return;
        unexported = true;
        invoker.destroy();
        System.out.println("[DubboExporter] 取消导出: " + invoker.getUrl().getServiceKey());
    }
}
