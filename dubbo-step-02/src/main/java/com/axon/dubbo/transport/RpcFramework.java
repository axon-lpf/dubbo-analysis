
package com.axon.dubbo.transport;

import java.io.*;
import java.lang.reflect.*;
import java.lang.reflect.Proxy;
import java.net.*;

public class RpcFramework {

    // 服务导出
    public static void export(Object service, int port) throws Exception {
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("服务启动，监听端口：" + port);

        while (true) {
            Socket socket = serverSocket.accept();
            new Thread(() -> {
                try (
                    ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
                    ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())
                ) {
                    String methodName = input.readUTF();
                    Class<?>[] paramTypes = (Class<?>[]) input.readObject();
                    Object[] args = (Object[]) input.readObject();

                    Method method = service.getClass().getMethod(methodName, paramTypes);
                    Object result = method.invoke(service, args);

                    output.writeObject(result);
                    output.flush();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }

    // 服务引用
    @SuppressWarnings("unchecked")
    public static <T> T refer(Class<T> interfaceClass, String host, int port) throws Exception {
        return (T) Proxy.newProxyInstance(
            interfaceClass.getClassLoader(),
            new Class<?>[]{interfaceClass},
            (proxy, method, args) -> {
                Socket socket = new Socket(host, port);
                try (
                    ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
                    ObjectInputStream input = new ObjectInputStream(socket.getInputStream())
                ) {
                    output.writeUTF(method.getName());
                    output.writeObject(method.getParameterTypes());
                    output.writeObject(args);

                    return input.readObject();
                }
            }
                                         );
    }
}
