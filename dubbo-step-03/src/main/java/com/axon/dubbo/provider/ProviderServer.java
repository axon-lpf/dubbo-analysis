
package com.axon.dubbo.provider;

import com.axon.dubbo.api.HelloService;
import com.axon.dubbo.provider.impl.HelloServiceImpl;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class ProviderServer {
    public static void main(String[] args) throws Exception {
        ServerSocket serverSocket = new ServerSocket(12345);
        System.out.println("服务端启动，监听端口 12345...");
        HelloService helloService = new HelloServiceImpl();

        while (true) {
            Socket socket = serverSocket.accept();
            new Thread(() -> {
                try (
                        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())
                ) {
                    // 读取客户端传来的方法名和参数
                    String method = (String) in.readObject();
                    String arg = (String) in.readObject();

                    String result = null;
                    // 只实现单一方法路由
                    if ("sayHello".equals(method)) {
                        result = helloService.sayHello(arg);
                    }
                    // 返回结果
                    out.writeObject(result);
                    out.flush();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try { socket.close(); } catch (Exception ignore) {}
                }
            }).start();
        }
    }

}
