package com.axon.dubbo.rpc.protocol;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.registry.RegistryService;
import com.axon.dubbo.remoting.exchange.DubboCodec;
import com.axon.dubbo.remoting.transport.socket.Request;
import com.axon.dubbo.remoting.transport.socket.Response;
import com.axon.dubbo.rpc.*;
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
 * Dubbo 协议实现（Step 09 升级版）
 *
 * 升级点：refer() 创建 RegistryDirectory 后，
 * 从 Directory.list() 获取全部 Provider Invoker 列表。
 * 当前仍手动选第一个，负载均衡策略在 Step 10 中引入。
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class DubboProtocol implements Protocol {

    private final Map<String, Exporter<?>> exporterMap = new ConcurrentHashMap<>();
    private final DubboCodec codec = new DubboCodec();
    private volatile boolean running = true;
    private final RegistryService registry;

    private final Map<String, RegistryDirectory<?>> directoryMap = new ConcurrentHashMap<>();

    public DubboProtocol(RegistryService registry) {
        this.registry = registry;
    }

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

    @Override
    @SuppressWarnings("unchecked")
    public <T> Invoker<T> refer(Class<T> type, URL url) {
        String serviceKey = url.getServiceKey();
        System.out.println("[DubboProtocol] 引用服务: " + serviceKey);

        RegistryDirectory<T> directory = new RegistryDirectory<>(
                type, url, registry,
                providerUrl -> new DubboInvoker<>(type, providerUrl, codec));
        directoryMap.put(serviceKey, directory);

        List<Invoker<T>> invokers = directory.list(null);
        if (invokers.isEmpty()) {
            throw new IllegalStateException("未找到服务 [" + serviceKey + "] 的可用提供者");
        }

        System.out.println("[DubboProtocol] 从 Directory 获取到 " + invokers.size() + " 个 Invoker");
        for (int i = 0; i < invokers.size(); i++) {
            System.out.println("[DubboProtocol]   [" + i + "] " + invokers.get(i).getUrl().getAddress());
        }

        // Step 09: 选第一个（Step 10 引入 LoadBalance 自动选择）
        return invokers.get(0);
    }

    @SuppressWarnings("unchecked")
    public <T> RegistryDirectory<T> getDirectory(String serviceKey) {
        return (RegistryDirectory<T>) directoryMap.get(serviceKey);
    }

    private void startServer(int port) {
        Thread t = new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(port)) {
                System.out.println("[DubboProtocol] 端口监听: " + port);
                while (running) {
                    try { handleClient(ss.accept()); } catch (Exception e) {
                        if (running) e.printStackTrace(); }
                }
            } catch (Exception e) { if (running) e.printStackTrace(); }
        }, "DubboProtocol-" + port);
        t.setDaemon(true); t.start();
    }

    private void handleClient(Socket c) {
        try (DataInputStream dis = new DataInputStream(c.getInputStream());
             DataOutputStream dos = new DataOutputStream(c.getOutputStream())) {
            int len = dis.readInt(); byte[] msg = new byte[len]; dis.readFully(msg);
            Request req = (Request) codec.decode(msg);
            Response resp = handle(req);
            byte[] out = codec.encode(resp);
            dos.writeInt(out.length); dos.write(out); dos.flush();
        } catch (Exception e) { System.err.println("[DubboProtocol] " + e.getMessage()); }
        finally { try { c.close(); } catch (IOException e) {} }
    }

    private Response handle(Request req) {
        try {
            RpcInvocation inv = new RpcInvocation(req.getInterfaceName(), req.getMethodName(),
                    req.getParameterTypes(), req.getArguments());
            Exporter<?> exp = exporterMap.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(req.getInterfaceName()))
                    .map(Map.Entry::getValue).findFirst().orElse(null);
            if (exp == null) return Response.error(req.getId(), "服务未找到", "IllegalStateException");
            Result r = exp.getInvoker().invoke(inv);
            if (r.hasException()) { Throwable t = r.getException(); return Response.error(req.getId(), t.getMessage(), t.getClass().getName()); }
            return Response.success(req.getId(), r.getValue());
        } catch (Exception e) { return Response.error(req.getId(), e.getMessage(), e.getClass().getName()); }
    }

    public void destroy() {
        running = false;
        for (RegistryDirectory<?> d : directoryMap.values()) d.destroy();
        directoryMap.clear();
        for (Exporter<?> e : exporterMap.values()) { registry.unregister(e.getInvoker().getUrl()); e.unexport(); }
        exporterMap.clear();
        System.out.println("[DubboProtocol] 已销毁");
    }
}
