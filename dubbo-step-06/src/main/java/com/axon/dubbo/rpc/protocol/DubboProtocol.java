package com.axon.dubbo.rpc.protocol;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.registry.RegistryService;
import com.axon.dubbo.remoting.exchange.DubboCodec;
import com.axon.dubbo.remoting.transport.socket.Request;
import com.axon.dubbo.remoting.transport.socket.Response;
import com.axon.dubbo.rpc.*;
import com.axon.dubbo.rpc.support.AbstractInvoker;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dubbo 协议实现（Step 06 升级版）
 *
 * 集成注册中心：
 * - export(): 创建 Exporter + 启动端口 → 向注册中心注册服务 URL
 * - refer():  从注册中心发现 Provider URL → 创建 DubboInvoker
 *
 * 这是 Dubbo 服务治理的核心流程：
 * Provider 启动 → 注册 → Consumer 发现 → 调用
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class DubboProtocol implements Protocol {

    private final Map<String, Exporter<?>> exporterMap = new ConcurrentHashMap<>();
    private final DubboCodec codec = new DubboCodec();
    private volatile boolean running = true;

    /**
     * 注册中心引用（Step 06 新增）
     */
    private final RegistryService registry;

    public DubboProtocol(RegistryService registry) {
        this.registry = registry;
    }

    // ==================== Provider 端：export + register ====================

    @Override
    public <T> Exporter<T> export(Invoker<T> invoker) {
        URL url = invoker.getUrl();
        String serviceKey = url.getServiceKey();

        // 1. 创建 Exporter
        Exporter<T> exporter = new DubboExporter<>(invoker);
        exporterMap.put(serviceKey, exporter);

        // 2. 启动端口监听
        startServer(url.getPort());

        // ====== Step 06 新增：向注册中心注册 ======
        registry.register(url);
        System.out.println("[DubboProtocol] 导出并注册服务: " + serviceKey + " | " + url);

        return exporter;
    }

    // ==================== Consumer 端：discover + refer ====================

    @Override
    public <T> Invoker<T> refer(Class<T> type, URL url) {
        System.out.println("[DubboProtocol] 引用服务: " + type.getName());

        // ====== Step 06 新增：从注册中心发现 Provider ======
        List<URL> providerUrls = registry.lookup(url);

        if (providerUrls.isEmpty()) {
            throw new IllegalStateException(
                    "未找到服务 [" + url.getServiceKey() + "] 的可用提供者，"
                            + "请检查 Provider 是否已启动并注册");
        }

        // 选择第一个 Provider（负载均衡在 Step 10 中实现）
        URL providerUrl = providerUrls.get(0);
        System.out.println("[DubboProtocol] 发现提供者: " + providerUrl.getAddress());

        // 创建 Consumer 端 Invoker
        DubboInvoker<T> invoker = new DubboInvoker<>(type, providerUrl, codec);
        return invoker;
    }

    // ==================== 服务端网络处理 ====================

    private void startServer(int port) {
        Thread serverThread = new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(port)) {
                System.out.println("[DubboProtocol] 端口已监听: " + port);
                while (running) {
                    try {
                        Socket client = ss.accept();
                        handleClient(client);
                    } catch (Exception e) {
                        if (running) e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                if (running) { e.printStackTrace(); }
            }
        }, "DubboProtocol-" + port);
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private void handleClient(Socket clientSocket) {
        try (DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
             DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream())) {

            int msgLen = dis.readInt();
            byte[] msgBytes = new byte[msgLen];
            dis.readFully(msgBytes);

            Request request = (Request) codec.decode(msgBytes);
            System.out.println("[DubboProtocol] 收到请求: " + request);

            Response response = handle(request);

            byte[] respBytes = codec.encode(response);
            dos.writeInt(respBytes.length);
            dos.write(respBytes);
            dos.flush();

        } catch (Exception e) {
            System.err.println("[DubboProtocol] 处理异常: " + e.getMessage());
        } finally {
            try { clientSocket.close(); } catch (IOException e) {}
        }
    }

    private Response handle(Request request) {
        try {
            RpcInvocation invocation = new RpcInvocation(
                    request.getInterfaceName(), request.getMethodName(),
                    request.getParameterTypes(), request.getArguments());

            Exporter<?> exporter = findExporter(request.getInterfaceName());
            if (exporter == null) {
                return Response.error(request.getId(), "服务未找到: " + request.getInterfaceName(),
                        "IllegalStateException");
            }

            Result result = exporter.getInvoker().invoke(invocation);

            if (result.hasException()) {
                Throwable t = result.getException();
                return Response.error(request.getId(), t.getMessage(), t.getClass().getName());
            }
            return Response.success(request.getId(), result.getValue());

        } catch (Exception e) {
            return Response.error(request.getId(), e.getMessage(), e.getClass().getName());
        }
    }

    private Exporter<?> findExporter(String interfaceName) {
        for (Map.Entry<String, Exporter<?>> e : exporterMap.entrySet()) {
            if (e.getKey().startsWith(interfaceName)) return e.getValue();
        }
        return null;
    }

    public void destroy() {
        running = false;
        for (Exporter<?> e : exporterMap.values()) {
            registry.unregister(e.getInvoker().getUrl()); // Step 06: 取消注册
            e.unexport();
        }
        exporterMap.clear();
        System.out.println("[DubboProtocol] 已销毁");
    }
}
