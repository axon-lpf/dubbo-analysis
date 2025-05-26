package com.axon.dubbo.provider;

import com.axon.dubbo.api.HelloService;
import com.axon.dubbo.common.RpcRequest;
import com.axon.dubbo.common.RpcResponse;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @author：liupengfei
 * @date：2025/5/26
 * @description：
 */
// provider/RpcServer.java
public class RpcServer {

    public static void main(String[] args) throws Exception {
        ServerSocket serverSocket = new ServerSocket(8080);
        System.out.println("服务端启动，监听端口 8080");

        while (true) {
            Socket socket = serverSocket.accept();
            new Thread(() -> {
                try (ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
                     ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())) {

                    RpcRequest request = (RpcRequest) input.readObject();

                    // 简化服务注册：只支持 HelloServiceImpl
                    HelloService service = new HelloServiceImpl();

                    Method method = service.getClass().getMethod(
                            request.getMethodName(), request.getParameterTypes());
                    Object result = method.invoke(service, request.getParameters());

                    RpcResponse response = new RpcResponse();
                    response.setResult(result);
                    output.writeObject(response);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }
}
