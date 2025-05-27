package com.axon.dubbo.provider;

import com.axon.dubbo.api.HelloService;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @author：liupengfei
 * @date：2025/5/27
 * @description：
 */
public class ProviderServer {

    public static void main(String[] args) throws IOException {
        HelloService helloService = new HelloServiceImpl();
        ServerSocket serverSocket = new ServerSocket(9000);
        System.out.println("服务端启动，监听端口 9000...");

        while (true) {
            Socket socket = serverSocket.accept();
            new Thread(() -> handleClient(socket, helloService)).start();
        }
    }

    private static void handleClient(Socket socket, HelloService service) {
        try (ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
             ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())) {
            // 读取方法参数
            String methodName = (String) input.readObject(); // sayHello
            String argument = (String) input.readObject();   // name

            if ("sayHello".equals(methodName)) {
                String result = service.sayHello(argument);
                output.writeObject(result);
                output.flush();
            } else {
                output.writeObject("Unsupported method: " + methodName);
                output.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
