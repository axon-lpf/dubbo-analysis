package com.axon.dubbo.core;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.Socket;


/**
 * @author：liupengfei
 * @date：2025/5/27
 * @description：
 */

public class RpcInvocationHandler implements InvocationHandler {

    private final String host;
    private final int port;
    private final Class<?> serviceInterface;

    public RpcInvocationHandler(String host, int port, Class<?> serviceInterface) {
        this.host = host;
        this.port = port;
        this.serviceInterface = serviceInterface;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try (Socket socket = new Socket(host, port);
             ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream input = new ObjectInputStream(socket.getInputStream())) {

            // 发送接口名、方法名、参数
            output.writeObject(serviceInterface.getName());
            output.writeObject(method.getName());
            output.writeObject(method.getParameterTypes());
            output.writeObject(args);
            output.flush();

            // 读取结果
            Object result = input.readObject();
            return result;
        }
    }
}
