package com.axon.dubbo.remoting.transport.socket;

import com.axon.dubbo.common.serialize.Serialization;
import com.axon.dubbo.common.serialize.java.JavaSerialization;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对象序列化服务端
 *
 * Step 03 增强：内置简单的服务注册表（接口名 → 实现类实例），
 * 收到请求后查找对应实现并反射调用，返回真实结果。
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class ObjectServer {

    private final int port;
    private final Serialization serialization;
    private volatile boolean running = true;

    /**
     * 服务注册表：接口全限定名 → 实现类实例
     *
     * 这是 Dubbo 中 ServiceRepository 的雏形。
     * 后续步骤会将其抽取为独立的 Registry 模块。
     */
    private final Map<String, Object> serviceMap = new ConcurrentHashMap<>();

    public ObjectServer(int port) {
        this.port = port;
        this.serialization = new JavaSerialization();
    }

    /**
     * 注册服务
     *
     * @param interfaceClass 接口类
     * @param implementation 实现类实例
     */
    public <T> void registerService(Class<T> interfaceClass, T implementation) {
        String interfaceName = interfaceClass.getName();
        serviceMap.put(interfaceName, implementation);
        System.out.println("[ObjectServer] 注册服务: " + interfaceName
                + " → " + implementation.getClass().getSimpleName());
    }

    public void start() {
        System.out.println("[ObjectServer] 服务端启动中，监听端口: " + port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("[ObjectServer] 服务端启动成功，等待客户端连接...");

            while (running) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[ObjectServer] 收到客户端连接: " + clientSocket.getInetAddress());
                handleClient(clientSocket);
            }
        } catch (Exception e) {
            System.err.println("[ObjectServer] 服务端异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleClient(Socket clientSocket) {
        try (DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
             DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream())) {

            // 读取请求
            int requestLength = dis.readInt();
            byte[] requestBytes = new byte[requestLength];
            dis.readFully(requestBytes);
            Request request = serialization.deserialize(requestBytes, Request.class);
            System.out.println("[ObjectServer] 收到请求: " + request);

            // 处理请求
            Response response = processRequest(request);

            // 返回响应
            byte[] responseBytes = serialization.serialize(response);
            dos.writeInt(responseBytes.length);
            dos.write(responseBytes);
            dos.flush();
            System.out.println("[ObjectServer] 返回响应: " + response);

        } catch (Exception e) {
            System.err.println("[ObjectServer] 处理请求异常: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try { clientSocket.close(); } catch (IOException e) { e.printStackTrace(); }
        }
    }

    /**
     * 处理请求：查找服务 → 反射调用 → 返回结果
     */
    private Response processRequest(Request request) {
        try {
            // 1. 查找服务实例
            String interfaceName = request.getInterfaceName();
            Object serviceImpl = serviceMap.get(interfaceName);
            if (serviceImpl == null) {
                return Response.error(request.getId(),
                        "服务未找到: " + interfaceName,
                        "java.lang.IllegalStateException");
            }

            // 2. 反射调用目标方法
            Class<?>[] paramTypes = resolveParameterTypes(request.getParameterTypes());
            java.lang.reflect.Method method = serviceImpl.getClass()
                    .getMethod(request.getMethodName(), paramTypes);
            Object result = method.invoke(serviceImpl, request.getArguments());

            return Response.success(request.getId(), result);

        } catch (Exception e) {
            return Response.error(request.getId(),
                    "服务调用异常: " + e.getMessage(),
                    e.getClass().getName());
        }
    }

    /**
     * 将全限定类名字符串数组转换为 Class[] 数组
     */
    private Class<?>[] resolveParameterTypes(String[] typeNames) throws ClassNotFoundException {
        if (typeNames == null || typeNames.length == 0) {
            return new Class<?>[0];
        }
        Class<?>[] types = new Class<?>[typeNames.length];
        for (int i = 0; i < typeNames.length; i++) {
            types[i] = resolvePrimitiveOrClass(typeNames[i]);
        }
        return types;
    }

    /**
     * 处理基本类型和包装类型
     */
    private Class<?> resolvePrimitiveOrClass(String typeName) throws ClassNotFoundException {
        switch (typeName) {
            case "boolean": return boolean.class;
            case "byte":    return byte.class;
            case "short":   return short.class;
            case "int":     return int.class;
            case "long":    return long.class;
            case "float":   return float.class;
            case "double":  return double.class;
            case "char":    return char.class;
            case "void":    return void.class;
            default:        return Class.forName(typeName);
        }
    }

    public void stop() {
        this.running = false;
    }
}
