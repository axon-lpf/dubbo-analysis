package com.axon.dubbo.protocol;

import com.axon.dubbo.core.codec.RpcRequest;
import com.axon.dubbo.core.codec.RpcResponse;
import com.axon.dubbo.remoting.NettyClient;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author：liupengfei
 * @date：2025/5/27
 * @description：
 */
public class RpcInvoker {

    private final String address; // ip:port
    private static final AtomicLong REQUEST_ID = new AtomicLong(0);

    public RpcInvoker(String address) {
        this.address = address;
    }

    public Object invoke(String interfaceName, String methodName, Class<?>[] paramTypes, Object[] args) {
        RpcRequest request = new RpcRequest();
        request.setRequestId(REQUEST_ID.incrementAndGet());
        request.setInterfaceName(interfaceName);
        request.setMethodName(methodName);
        request.setParamTypes(paramTypes);
        request.setParameters(args);

        RpcResponse response = NettyClient.sendRequest(address, request);
        if (response.getException() != null) {
            throw new RuntimeException("调用远程服务异常", response.getException());
        }
        return response.getResult();
    }
}
