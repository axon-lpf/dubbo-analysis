package com.axon.dubbo.rpc.protocol;

import com.axon.dubbo.rpc.Exporter;
import com.axon.dubbo.rpc.Invoker;

/**
 * Dubbo 协议导出器
 *
 * 持有被导出的 Invoker 引用。
 * unexport() 时销毁 Invoker，释放资源。
 *
 * @param <T> 服务接口类型
 * @author axon-dubbo
 * @since 1.0.0
 */
public class DubboExporter<T> implements Exporter<T> {

    private final Invoker<T> invoker;

    /**
     * 标记是否已取消导出
     */
    private volatile boolean unexported = false;

    public DubboExporter(Invoker<T> invoker) {
        this.invoker = invoker;
    }

    @Override
    public Invoker<T> getInvoker() {
        return invoker;
    }

    @Override
    public void unexport() {
        if (unexported) {
            return;
        }
        unexported = true;
        invoker.destroy();
        System.out.println("[DubboExporter] 取消导出服务: " + invoker.getUrl().getServiceKey());
    }
}
