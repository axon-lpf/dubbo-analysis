package com.axon.dubbo.remoting.transport.socket;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.common.serialize.Serialization;
import com.axon.dubbo.common.serialize.java.JavaSerialization;
import com.axon.dubbo.rpc.*;
import com.axon.dubbo.rpc.protocol.DubboExporter;
import com.axon.dubbo.rpc.proxy.ProxyFactory;
import com.axon.dubbo.rpc.proxy.jdk.JdkProxyFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 Invoker/Exporter 体系的服务端
 *
 * 相比 Step 03 的 ObjectServer，核心变化：
 *
 * Step 03（临时方案）：
 *   Map<String, Object> serviceMap  → 手动反射调用
 *
 * Step 04（正式方案）：
 *   Map<String, Exporter<?>> exporterMap  → Invoker.invoke()
 *   每个服务导出为 Exporter，内部持有 Invoker，
 *   请求分发时通过 Invoker 的标准接口执行调用。
 *
 * 这个架构的好处：
 * 1. Invoker 是可组合的：可以在 Invoker 上叠 Filter、Listener 等
 * 2. Exporter 管理生命周期：unexport 时统一清理资源
 * 3. URL 承载完整配置：服务元数据不再散落在各处
 *
 * 对应官方源码的核心概念：
 * - org.apache.dubbo.rpc.Exporter
 * - org.apache.dubbo.rpc.Invoker
 * - org.apache.dubbo.rpc.protocol.dubbo.DubboProtocol
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class ExporterServer {

    private final int port;
    private final Serialization serialization;
    private final ProxyFactory proxyFactory;
    private volatile boolean running = true;

    /**
     * 导出器注册表：服务标识（接口名） → Exporter
     */
    private final Map<String, Exporter<?>> exporterMap = new ConcurrentHashMap<>();

    public ExporterServer(int port) {
        this.port = port;
        this.serialization = new JavaSerialization();
        this.proxyFactory = new JdkProxyFactory();
    }

    /**
     * 导出服务
     *
     * 将本地实现类包装为 Invoker → 创建 Exporter → 存入注册表
     *
     * @param interfaceClass 服务接口
     * @param implementation 实现类实例
     * @param <T>            接口类型
     * @return Exporter 导出器（可用于后续取消导出）
     */
    public <T> Exporter<T> export(Class<T> interfaceClass, T implementation) {
        // 1. 构建服务 URL
        URL url = URL.builder()
                .protocol("dubbo")
                .host("127.0.0.1")
                .port(port)
                .path(interfaceClass.getName())
                .addParameter("version", "1.0.0")
                .build();

        // 2. 创建 Provider 端 Invoker（反射调用本地实现）
        Invoker<T> invoker = proxyFactory.getInvoker(implementation, interfaceClass, url);

        // 3. 导出为 Exporter
        Exporter<T> exporter = new DubboExporter<>(invoker);

        // 4. 注册到服务表
        String serviceKey = url.getServiceKey();
        exporterMap.put(serviceKey, exporter);
        System.out.println("[ExporterServer] 导出服务: " + serviceKey
                + " → " + implementation.getClass().getSimpleName()
                + " | URL: " + url);

        return exporter;
    }

    /**
     * 启动服务端
     */
    public void start() {
        System.out.println("[ExporterServer] 启动中，监听端口: " + port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("[ExporterServer] 启动成功，等待请求...");

            while (running) {
                Socket clientSocket = serverSocket.accept();
                handleClient(clientSocket);
            }
        } catch (Exception e) {
            System.err.println("[ExporterServer] 异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 处理客户端请求
     */
    private void handleClient(Socket clientSocket) {
        try (DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
             DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream())) {

            // 1. 读取并反序列化 Request
            int reqLen = dis.readInt();
            byte[] reqBytes = new byte[reqLen];
            dis.readFully(reqBytes);
            Request request = serialization.deserialize(reqBytes, Request.class);
            System.out.println("[ExporterServer] 收到请求: " + request);

            // 2. 处理请求：查找 Exporter → Invoker.invoke()
            Response response = handle(request);

            // 3. 序列化并发送 Response
            byte[] respBytes = serialization.serialize(response);
            dos.writeInt(respBytes.length);
            dos.write(respBytes);
            dos.flush();

        } catch (Exception e) {
            System.err.println("[ExporterServer] 处理请求异常: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try { clientSocket.close(); } catch (IOException e) { e.printStackTrace(); }
        }
    }

    /**
     * 请求分发：Request → Invoker.invoke() → Response
     *
     * 这是服务端处理的核心流程：
     * 1. 从 Request 中提取调用信息，构建 RpcInvocation
     * 2. 根据接口名查找对应的 Exporter
     * 3. 通过 Exporter.getInvoker().invoke(invocation) 执行调用
     * 4. 将 Invoker 返回的 Result 转换为 Response
     */
    private Response handle(Request request) {
        try {
            // 1. 构建 Invocation
            RpcInvocation invocation = new RpcInvocation(
                    request.getInterfaceName(),
                    request.getMethodName(),
                    request.getParameterTypes(),
                    request.getArguments()
            );

            // 2. 查找 Exporter（遍历 exporterMap 匹配）
            Exporter<?> exporter = findExporter(request.getInterfaceName());
            if (exporter == null) {
                return Response.error(request.getId(),
                        "服务未找到: " + request.getInterfaceName(),
                        "java.lang.IllegalStateException");
            }

            // 3. 通过 Invoker 执行调用
            Invoker<?> invoker = exporter.getInvoker();
            Result result = invoker.invoke(invocation);

            // 4. 从 Result 提取结果
            if (result.hasException()) {
                Throwable t = result.getException();
                return Response.error(request.getId(),
                        t.getMessage(),
                        t.getClass().getName());
            } else {
                return Response.success(request.getId(), result.getValue());
            }

        } catch (Exception e) {
            return Response.error(request.getId(),
                    "请求处理异常: " + e.getMessage(),
                    e.getClass().getName());
        }
    }

    /**
     * 在导出器注册表中查找匹配的 Exporter
     */
    private Exporter<?> findExporter(String interfaceName) {
        // 先精确匹配
        for (Map.Entry<String, Exporter<?>> entry : exporterMap.entrySet()) {
            if (entry.getKey().startsWith(interfaceName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 关闭服务端，取消所有导出
     */
    public void stop() {
        this.running = false;
        for (Exporter<?> exporter : exporterMap.values()) {
            exporter.unexport();
        }
        exporterMap.clear();
        System.out.println("[ExporterServer] 已停止，所有服务已取消导出");
    }
}
