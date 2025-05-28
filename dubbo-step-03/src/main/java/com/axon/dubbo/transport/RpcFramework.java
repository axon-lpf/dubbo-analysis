
package com.axon.dubbo.transport;


import com.axon.dubbo.core.Invoker;
import com.axon.dubbo.core.Result;
import com.axon.dubbo.core.RpcInvocation;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class RpcFramework {

    public static <T> void export(Invoker<T> invoker) throws Exception {
        int port = 1234;
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

                    RpcInvocation invocation = new RpcInvocation(methodName, paramTypes, args);
                    Result result = invoker.invoke(invocation);

                    output.writeObject(result.getValue());
                    output.flush();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }
}
