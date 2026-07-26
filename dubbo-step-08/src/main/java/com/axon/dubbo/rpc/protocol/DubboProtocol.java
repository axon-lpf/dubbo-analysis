package com.axon.dubbo.rpc.protocol;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.registry.RegistryService;
import com.axon.dubbo.remoting.exchange.DubboCodec;
import com.axon.dubbo.remoting.transport.socket.Request;
import com.axon.dubbo.remoting.transport.socket.Response;
import com.axon.dubbo.rpc.*;
import com.axon.dubbo.rpc.cluster.Directory;
import com.axon.dubbo.rpc.cluster.directory.RegistryDirectory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dubbo 协议实现（Step 07 升级版）
 *
 * 升级点：Consumer 端引入 Directory 动态服务发现
 *
 * Step 06: refer() → registry.lookup() → 选第一个 Provider → DubboInvoker
 * Step 07: refer() → 创建 RegistryDirectory（订阅注册中心）
 *           → Directory.notify() 自动感知 Provider 变更
 *           → Directory.list() 获取最新 Invoker 列表
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class DubboProtocol implements Protocol {

    private final Map<String, Exporter<?>> exporterMap = new ConcurrentHashMap<>();
    private final DubboCodec codec = new DubboCodec();
    private volatile boolean running = true;
    private final RegistryService registry;

    /**
     * Consumer 端的服务目录缓存（服务标识 → Directory）
     * Directory 内部订阅了注册中心，Provider 变更时自动刷新
     */
    private final Map<String, RegistryDirectory<?>> directoryMap = new ConcurrentHashMap<>();

    public DubboProtocol(RegistryService registry) {
        this.registry = registry;
    }

    // ==================== Provider 端 ====================

    @Override
    public <T> Exporter<T> export(Invoker<T> invoker) {
        URL url = invoker.getUrl();
        String serviceKey = url.getServiceKey();

        Exporter<T> exporter = new DubboExporter<>(invoker);
        exporterMap.put(serviceKey, exporter);
        startServer(url.getPort());
        registry.register(url);

        System.out.println("[DubboProtocol] 导出并注册: " + serviceKey + " | " + url);
        return exporter;
    }

    // ==================== Consumer 端（Step 07 升级） ====================

    @Override
    @SuppressWarnings("unchecked")
    public <T> Invoker<T> refer(Class<T> type, URL url) {
        String serviceKey = url.getServiceKey();
        System.out.println("[DubboProtocol] 引用服务: " + serviceKey);

        // ====== Step 07：创建 RegistryDirectory（订阅 + 动态发现） ======
        // Directory 构造时会立即订阅注册中心，注册中心推送当前 Provider 列表
        RegistryDirectory<T> directory = new RegistryDirectory<>(
                type, url, registry,
                // URL → Invoker 的工厂方法
                providerUrl -> new DubboInvoker<>(type, providerUrl, codec)
        );

        directoryMap.put(serviceKey, directory);

        // 从 Directory 获取 Invoker 列表，取第一个创建代理
        // 注：负载均衡在 Step 10 实现，目前始终选第一个
        List<Invoker<T>> invokers = directory.list(null);
        if (invokers.isEmpty()) {
            throw new IllegalStateException(
                    "未找到服务 [" + serviceKey + "] 的可用提供者");
        }

        System.out.println("[DubboProtocol] 从 Directory 获取到 "
                + invokers.size() + " 个 Invoker");

        return invokers.get(0);
    }

    // ==================== 直接创建 Invoker（供 Directory 回调使用） ====================

    /**
     * 将 Provider URL 直接转换为 Invoker
     *
     * 不走注册中心 lookup，用于 Directory.notify() 的回调场景。
     * 因为 notify 的入参已经是注册中心推送的 Provider URL 列表，
     * 不需要也不应该再去注册中心查询（避免死循环）。
     */
    public <T> Invoker<T> createInvokerDirectly(Class<T> type, URL providerUrl) {
        return new DubboInvoker<>(type, providerUrl, codec);
    }

    // ==================== 获取 Directory（供后续步骤使用） ====================

    @SuppressWarnings("unchecked")
    public <T> RegistryDirectory<T> getDirectory(String serviceKey) {
        return (RegistryDirectory<T>) directoryMap.get(serviceKey);
    }

    // ==================== 服务端网络处理 ====================

    private void startServer(int port) {
        Thread serverThread = new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(port)) {
                System.out.println("[DubboProtocol] 端口监听: " + port);
                while (running) {
                    try {
                        Socket client = ss.accept();
                        handleClient(client);
                    } catch (Exception e) {
                        if (running) e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                if (running) e.printStackTrace();
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
            Response response = handle(request);
            byte[] respBytes = codec.encode(response);
            dos.writeInt(respBytes.length);
            dos.write(respBytes);
            dos.flush();
        } catch (Exception e) {
            System.err.println("[DubboProtocol] 异常: " + e.getMessage());
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
                return Response.error(request.getId(),
                        "服务未找到: " + request.getInterfaceName(), "IllegalStateException");
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

    // ==================== 生命周期 ====================

    public void destroy() {
        running = false;
        for (RegistryDirectory<?> dir : directoryMap.values()) {
            dir.destroy(); // 取消注册中心订阅
        }
        directoryMap.clear();
        for (Exporter<?> e : exporterMap.values()) {
            registry.unregister(e.getInvoker().getUrl());
            e.unexport();
        }
        exporterMap.clear();
        System.out.println("[DubboProtocol] 已销毁");
    }
}
