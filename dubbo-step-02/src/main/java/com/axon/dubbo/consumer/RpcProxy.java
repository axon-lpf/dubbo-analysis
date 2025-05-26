package com.axon.dubbo.consumer;

import com.axon.dubbo.common.RpcRequest;
import com.axon.dubbo.common.RpcResponse;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Proxy;
import java.net.Socket;

/**
 * @author：liupengfei
 * @date：2025/5/26
 * @description：
 */
// consumer/RpcProxy.java
public class RpcProxy {
    public static <T> T getProxy(Class<T> interfaceClass) {
        T localhost = (T) Proxy.newProxyInstance(interfaceClass.getClassLoader(), new Class[]{ interfaceClass },
                                                 (proxy, method, args) -> {
                                                     RpcRequest request = new RpcRequest();
                                                     request.setClassName(interfaceClass.getName());
                                                     request.setMethodName(method.getName());
                                                     request.setParameterTypes(method.getParameterTypes());
                                                     request.setParameters(args);

                                                     try (Socket socket = new Socket("localhost", 8080);
                                                          ObjectOutputStream output = new ObjectOutputStream(
                                                                  socket.getOutputStream());
                                                          ObjectInputStream input = new ObjectInputStream(
                                                                  socket.getInputStream())) {

                                                         output.writeObject(request);
                                                         RpcResponse response = (RpcResponse) input.readObject();

                                                         if (response.getException() != null) {
                                                             throw response.getException();
                                                         }

                                                         return response.getResult();
                                                     }
                                                 });
        return localhost;
    }
}
