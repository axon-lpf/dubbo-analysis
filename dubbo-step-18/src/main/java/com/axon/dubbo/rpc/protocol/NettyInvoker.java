package com.axon.dubbo.rpc.protocol;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.remoting.exchange.DubboCodec;
import com.axon.dubbo.remoting.transport.netty.NettyClient;
import com.axon.dubbo.remoting.transport.socket.Request;
import com.axon.dubbo.remoting.transport.socket.Response;
import com.axon.dubbo.rpc.*;
import com.axon.dubbo.rpc.support.AbstractInvoker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Netty 远程 Invoker（Step 16 升级版）
 *
 * 用 NettyClient 替换 BIO Socket。
 *
 * 对比：
 * Step 05 BIO:  new Socket(host, port) → 读取写入 → close()
 * Step 16 NIO:  NettyClient.send(request) → 长连接复用 + NIO 多路复用
 *
 * @param <T> 服务接口类型
 * @author axon-dubbo
 * @since 1.0.0
 */
public class NettyInvoker<T> extends AbstractInvoker<T> {

    private static final AtomicLong REQUEST_ID = new AtomicLong(0);
    private final DubboCodec codec;

    /**
     * Netty 客户端（长连接，按 host:port 缓存复用）
     */
    private static final Map<String, NettyClient> CLIENT_CACHE = new ConcurrentHashMap<>();

    public NettyInvoker(Class<T> type, URL url, DubboCodec codec) {
        super(type, url);
        this.codec = codec;
    }

    @Override
    protected Result doInvoke(Invocation invocation) throws Throwable {
        URL url = getUrl();

        Request request = new Request(
                REQUEST_ID.incrementAndGet(),
                invocation.getServiceName(),
                invocation.getMethodName(),
                invocation.getParameterTypes(),
                invocation.getArguments());

        System.out.println("[NettyInvoker] NIO 调用 → " + url.getAddress()
                + " | " + invocation.getMethodName());

        // 获取或创建 Netty 客户端（按地址缓存，连接复用）
        String key = url.getAddress();
        NettyClient client = CLIENT_CACHE.computeIfAbsent(key, k -> {
            NettyClient c = new NettyClient(url.getHost(), url.getPort(), codec);
            c.connect();
            return c;
        });

        // 同步发送请求
        Response response = client.send(request);

        if (response.isSuccess()) {
            return new RpcResult(response.getResult());
        } else {
            return new RpcResult(new RuntimeException(response.getErrorMessage()));
        }
    }
}
