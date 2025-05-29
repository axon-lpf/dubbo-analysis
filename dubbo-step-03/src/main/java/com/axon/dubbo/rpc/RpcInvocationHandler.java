package com.axon.dubbo.rpc;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.Socket;

/**
 * @author：liupengfei
 * @date：2025/5/29
 * @description：
 */
public class RpcInvocationHandler implements InvocationHandler {
    private final String host;
    private final int port;

    public RpcInvocationHandler(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try (Socket socket = new Socket(host, port)) {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            // 发送方法名和参数（只支持一个String参数的简单版本）
            out.writeObject(method.getName());
            out.writeObject(args[0]);
            out.flush();

            // 接收返回值
            Object result = in.readObject();
            return result;
        }
    }
}