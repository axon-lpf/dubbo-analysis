
package com.axon.dubbo.protocol;

import com.axon.dubbo.core.Invoker;
import com.axon.dubbo.transport.RemoteInvoker;
import com.axon.dubbo.transport.RpcFramework;

public class RpcProtocol implements Protocol {
    @Override
    public <T> Exporter<T> export(Invoker<T> invoker) {
        try {
            //通过rpc框架暴露服务
            RpcFramework.export(invoker);
        } catch (Exception e) {
            throw new RuntimeException("导出服务失败", e);
        }
        return () -> {};
    }

    @Override
    public <T> Invoker<T> refer(Class<T> interfaceClass, String host, int port) {
        return new RemoteInvoker<>(interfaceClass, host, port);
    }
}
