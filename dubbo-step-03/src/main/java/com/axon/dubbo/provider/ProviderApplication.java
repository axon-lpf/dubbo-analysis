
package com.axon.dubbo.provider;

import com.axon.dubbo.api.HelloService;
import com.axon.dubbo.core.Invocation;
import com.axon.dubbo.core.Invoker;
import com.axon.dubbo.core.RpcResult;
import com.axon.dubbo.protocol.Protocol;
import com.axon.dubbo.protocol.RpcProtocol;
import com.axon.dubbo.core.Result;

import java.lang.reflect.Method;

public class ProviderApplication {
    public static void main(String[] args) {
        HelloService service = new HelloServiceImpl();

        Invoker<HelloService> invoker = new Invoker<HelloService>() {
            public Class<HelloService> getInterface() {
                return HelloService.class;
            }

            public Result invoke(Invocation invocation) {
                try {
                    Method method = service.getClass().getMethod(invocation.getMethodName(), invocation.getParameterTypes());
                    Object value = method.invoke(service, invocation.getArguments());
                    return new RpcResult(value);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };

        Protocol protocol = new RpcProtocol();
        //暴露服务
        protocol.export(invoker);
    }
}
