package com.axon.dubbo.provider;

import com.axon.dubbo.api.HelloService;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

/**
 * @author：liupengfei
 * @date：2025/5/27
 * @description：
 */
public class ProviderServer {

    // 注册服务集合
    private static final Map<String, Object> serviceRegistry = new HashMap<>();


    static {
        serviceRegistry.put(HelloService.class.getName(), new HelloServiceImpl());
        // 未来可注册更多服务
    }

    public static void main(String[] args) throws Exception {
        ServerSocket serverSocket = new ServerSocket(9000);
        System.out.println("服务端启动，监听端口 9000...");

        while (true) {
            Socket socket = serverSocket.accept();
            new Thread(() -> handle(socket)).start();
        }
    }

    private static void handle(Socket socket) {
        try (
                ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
                ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())
        ) {
            // 接收调用信息
            String interfaceName = (String) input.readObject();
            String methodName = (String) input.readObject();
            Class<?>[] paramTypes = (Class<?>[]) input.readObject();
            Object[] args = (Object[]) input.readObject();

            // 根据接口名找到服务实现对象
            Object service = serviceRegistry.get(interfaceName);
            if (service == null) {
                output.writeObject(new RuntimeException("服务未找到: " + interfaceName));
                output.flush();
                return;
            }

            // 反射调用方法
            Method method = service.getClass().getMethod(methodName, paramTypes);
            Object result = method.invoke(service, args);

            // 返回结果
            output.writeObject(result);
            output.flush();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
