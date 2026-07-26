package com.axon.dubbo.rpc.protocol;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.remoting.Codec;
import com.axon.dubbo.remoting.exchange.DubboCodec;
import com.axon.dubbo.remoting.transport.socket.Request;
import com.axon.dubbo.remoting.transport.socket.Response;
import com.axon.dubbo.rpc.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dubbo 协议实现 —— Protocol 接口的核心实现
 *
 * 整合了 Provider 端的服务导出和 Consumer 端的服务引用。
 * 是 Step 01-04 中 ExporterServer 和 ObjectClient 的正式演进版本。
 *
 * 核心能力：
 * - export():  开启端口 → 注册 Invoker → 接收请求 → Codec 解码 → Invoker.invoke() → Codec 编码 → 返回
 * - refer():   创建 DubboInvoker → 调用时通过网络发送请求到 Provider
 *
 * 对应官方源码：org.apache.dubbo.rpc.protocol.dubbo.DubboProtocol
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class DubboProtocol implements Protocol {

    /**
     * 导出器注册表：服务标识 → Exporter
     */
    private final Map<String, Exporter<?>> exporterMap = new ConcurrentHashMap<>();

    /**
     * 编解码器
     */
    private final Codec codec;

    /**
     * 服务端运行标记
     */
    private volatile boolean running = true;

    public DubboProtocol() {
        this.codec = new DubboCodec();
    }

    public DubboProtocol(Codec codec) {
        this.codec = codec;
    }

    // ==================== Provider 端：export ====================

    @Override
    public <T> Exporter<T> export(Invoker<T> invoker) {
        URL url = invoker.getUrl();
        String serviceKey = url.getServiceKey();

        // 1. 创建 Exporter
        Exporter<T> exporter = new DubboExporter<>(invoker);

        // 2. 注册到本地
        exporterMap.put(serviceKey, exporter);
        System.out.println("[DubboProtocol] 导出服务: " + serviceKey + " | URL: " + url);

        // 3. 启动服务端端口监听
        startServer(url.getPort());

        return exporter;
    }

    /**
     * 启动服务端监听（每个端口只启动一次）
     */
    private void startServer(int port) {
        Thread serverThread = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                System.out.println("[DubboProtocol] 服务端启动成功，端口: " + port);

                while (running) {
                    Socket client = serverSocket.accept();
                    handleClient(client);
                }
            } catch (Exception e) {
                if (running) {
                    System.err.println("[DubboProtocol] 服务端异常: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }, "DubboProtocol-Server-" + port);
        serverThread.setDaemon(true);
        serverThread.start();
    }

    /**
     * 处理客户端请求
     */
    private void handleClient(Socket clientSocket) {
        try (DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
             DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream())) {

            // 1. 读取协议消息（长度前缀 + 协议消息）
            int msgLength = dis.readInt();
            byte[] msgBytes = new byte[msgLength];
            dis.readFully(msgBytes);

            // 2. 解码（Dubbo 协议字节数组 → Request）
            Request request = (Request) codec.decode(msgBytes);
            System.out.println("[DubboProtocol] 收到请求: " + request);

            // 3. 处理请求
            Response response = handle(request);

            // 4. 编码响应（Response → Dubbo 协议字节数组）
            byte[] encodedResponse = codec.encode(response);

            // 5. 发送响应（长度前缀 + 协议消息）
            dos.writeInt(encodedResponse.length);
            dos.write(encodedResponse);
            dos.flush();

        } catch (Exception e) {
            System.err.println("[DubboProtocol] 处理请求异常: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try { clientSocket.close(); } catch (IOException e) { e.printStackTrace(); }
        }
    }

    /**
     * 请求分发：Request → Invoker.invoke() → Response
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

            // 2. 查找 Exporter
            Exporter<?> exporter = findExporter(request.getInterfaceName());
            if (exporter == null) {
                return Response.error(request.getId(),
                        "服务未找到: " + request.getInterfaceName(),
                        "java.lang.IllegalStateException");
            }

            // 3. 调用 Invoker
            Invoker<?> invoker = exporter.getInvoker();
            Result result = invoker.invoke(invocation);

            // 4. Result → Response
            if (result.hasException()) {
                Throwable t = result.getException();
                return Response.error(request.getId(), t.getMessage(), t.getClass().getName());
            } else {
                return Response.success(request.getId(), result.getValue());
            }

        } catch (Exception e) {
            return Response.error(request.getId(), e.getMessage(), e.getClass().getName());
        }
    }

    private Exporter<?> findExporter(String interfaceName) {
        for (Map.Entry<String, Exporter<?>> entry : exporterMap.entrySet()) {
            if (entry.getKey().startsWith(interfaceName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    // ==================== Consumer 端：refer ====================

    @Override
    public <T> Invoker<T> refer(Class<T> type, URL url) {
        System.out.println("[DubboProtocol] 引用服务: " + type.getName() + " | URL: " + url);

        // 创建 Consumer 端 Invoker（通过网络调用远程 Provider）
        DubboInvoker<T> invoker = new DubboInvoker<>(type, url, codec);

        // 后续步骤会在这里加入 Cluster、Filter 等包装
        return invoker;
    }

    // ==================== 生命周期 ====================

    public void destroy() {
        this.running = false;
        for (Exporter<?> exporter : exporterMap.values()) {
            exporter.unexport();
        }
        exporterMap.clear();
        System.out.println("[DubboProtocol] 已销毁");
    }
}
