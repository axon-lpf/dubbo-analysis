package com.axon.dubbo.rpc.protocol;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.common.extension.ExtensionLoader;
import com.axon.dubbo.registry.RegistryService;
import com.axon.dubbo.remoting.exchange.DubboCodec;
import com.axon.dubbo.remoting.transport.netty.NettyServer;
import com.axon.dubbo.remoting.transport.socket.Request;
import com.axon.dubbo.remoting.transport.socket.Response;
import com.axon.dubbo.rpc.*;
import com.axon.dubbo.rpc.cluster.Cluster;
import com.axon.dubbo.rpc.cluster.directory.RegistryDirectory;
import com.axon.dubbo.rpc.cluster.support.FailoverCluster;
import com.axon.dubbo.rpc.support.AbstractInvoker;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dubbo 协议实现（Step 16 升级版）
 *
 * 核心升级：用 Netty NIO 替换 BIO ServerSocket
 *
 * Step 14: ServerSocket.accept() 阻塞
 * Step 16: NettyServer (NioEventLoopGroup) 非阻塞多路复用
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class DubboProtocol implements Protocol {

    private final Map<String, Exporter<?>> exporterMap = new ConcurrentHashMap<>();
    private final DubboCodec codec = new DubboCodec();
    private final RegistryService registry;
    private final Map<String, RegistryDirectory<?>> directoryMap = new ConcurrentHashMap<>();
    private final Map<Integer, NettyServer> serverMap = new ConcurrentHashMap<>();

    public DubboProtocol(RegistryService registry) {
        this.registry = registry;
    }

    // ==================== Provider 端（Step 16: Netty） ====================

    @Override
    public <T> Exporter<T> export(Invoker<T> invoker) {
        URL url = invoker.getUrl();
        String serviceKey = url.getServiceKey();

        Invoker<T> filteredInvoker = buildProviderFilterChain(invoker);
        Exporter<T> exporter = new DubboExporter<>(filteredInvoker);
        exporterMap.put(serviceKey, exporter);

        // Step 16: NettyServer 代替 ServerSocket
        startNettyServer(url.getPort());

        registry.register(url);
        System.out.println("[DubboProtocol] 导出并注册(NIO): " + serviceKey + " | " + url);
        return exporter;
    }

    private void startNettyServer(int port) {
        serverMap.computeIfAbsent(port, p -> {
            NettyServer server = new NettyServer(port, codec, this::handle);
            Thread t = new Thread(server::start, "NettyServer-" + port);
            t.setDaemon(true);
            t.start();
            // 等待 Netty 启动
            try { Thread.sleep(300); } catch (Exception ignored) {}
            return server;
        });
    }

    /** 请求处理 */
    private Response handle(Request req) {
        try {
            RpcInvocation inv = new RpcInvocation(req.getInterfaceName(), req.getMethodName(),
                    req.getParameterTypes(), req.getArguments());
            Exporter<?> exp = exporterMap.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(req.getInterfaceName()))
                    .map(Map.Entry::getValue).findFirst().orElse(null);
            if (exp == null) return Response.error(req.getId(), "服务未找到", "IllegalStateException");
            Result r = exp.getInvoker().invoke(inv);
            if (r.hasException()) {
                Throwable t = r.getException();
                return Response.error(req.getId(), t.getMessage(), t.getClass().getName());
            }
            return Response.success(req.getId(), r.getValue());
        } catch (Exception e) {
            return Response.error(req.getId(), e.getMessage(), e.getClass().getName());
        }
    }

    // ==================== Consumer 端（Step 16: NettyInvoker） ====================

    @Override
    @SuppressWarnings("unchecked")
    public <T> Invoker<T> refer(Class<T> type, URL url) {
        String serviceKey = url.getServiceKey();

        RegistryDirectory<T> directory = new RegistryDirectory<>(type, url, registry,
                providerUrl -> new NettyInvoker<>(type, providerUrl, codec));
        directoryMap.put(serviceKey, directory);

        Cluster cluster = new FailoverCluster();
        Invoker<T> clusterInvoker = cluster.join(directory);
        Invoker<T> filteredInvoker = buildConsumerFilterChain(clusterInvoker);

        List<Invoker<T>> invokers = directory.list(null);
        System.out.println("[DubboProtocol] 引用服务(NIO): " + serviceKey
                + " | Provider: " + invokers.size());

        return filteredInvoker;
    }

    @SuppressWarnings("unchecked")
    public <T> RegistryDirectory<T> getDirectory(String key) {
        return (RegistryDirectory<T>) directoryMap.get(key);
    }

    // ==================== Filter 链 ====================

    private <T> Invoker<T> buildProviderFilterChain(Invoker<T> invoker) {
        List<Filter> filters = ExtensionLoader.getExtensionLoader(Filter.class)
                .getActivateExtension("provider");
        return buildFilterChain(filters, invoker, "Provider");
    }

    private <T> Invoker<T> buildConsumerFilterChain(Invoker<T> invoker) {
        List<Filter> filters = ExtensionLoader.getExtensionLoader(Filter.class)
                .getActivateExtension("consumer");
        return buildFilterChain(filters, invoker, "Consumer");
    }

    @SuppressWarnings("unchecked")
    private <T> Invoker<T> buildFilterChain(List<Filter> filters, Invoker<T> last, String side) {
        if (filters.isEmpty()) return last;
        final Invoker<T> original = last;
        for (int i = filters.size() - 1; i >= 0; i--) {
            final Filter filter = filters.get(i);
            final Invoker<T> next = last;
            last = new AbstractInvoker<T>(original.getInterface(), original.getUrl()) {
                @Override protected Result doInvoke(Invocation inv) { return filter.invoke(next, inv); }
            };
        }
        return last;
    }

    // ==================== 生命周期 ====================

    public void destroy() {
        for (NettyServer s : serverMap.values()) s.close();
        serverMap.clear();
        for (RegistryDirectory<?> d : directoryMap.values()) d.destroy();
        directoryMap.clear();
        for (Exporter<?> e : exporterMap.values()) {
            registry.unregister(e.getInvoker().getUrl());
            e.unexport();
        }
        exporterMap.clear();
        System.out.println("[DubboProtocol] 已销毁");
    }
}
