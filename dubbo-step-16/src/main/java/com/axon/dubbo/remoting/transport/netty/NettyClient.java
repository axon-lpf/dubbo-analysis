package com.axon.dubbo.remoting.transport.netty;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.remoting.exchange.DubboCodec;
import com.axon.dubbo.remoting.transport.socket.Request;
import com.axon.dubbo.remoting.transport.socket.Response;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Netty 客户端
 *
 * 用 Netty NIO 替换 BIO Socket。
 *
 * Channel 复用：
 *   同一 host:port 的多个请求复用同一个 Netty Channel，
 *   避免频繁建立/断开连接（TCP 长连接）。
 *
 * 请求-响应匹配：
 *   通过 Request ID 将异步响应匹配到对应的 Future。
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class NettyClient {

    private final String host;
    private final int port;
    private final DubboCodec codec;
    private EventLoopGroup group;
    private Channel channel;

    /**
     * 等待响应的 Future Map
     * Key: requestId → Value: CompletableFuture
     */
    private final Map<Long, CompletableFuture<Response>> pendingRequests
            = new ConcurrentHashMap<>();

    public NettyClient(String host, int port, DubboCodec codec) {
        this.host = host;
        this.port = port;
        this.codec = codec;
    }

    /**
     * 建立连接
     */
    public void connect() {
        group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(new LengthFieldBasedFrameDecoder(
                                            1024 * 1024, 0, 4, 0, 4))
                                    .addLast(new NettyCodecHandler(codec))
                                    .addLast(new NettyClientHandler(pendingRequests));
                        }
                    });

            ChannelFuture future = bootstrap.connect(host, port).sync();
            channel = future.channel();
            System.out.println("[NettyClient] 连接成功: " + host + ":" + port);

        } catch (Exception e) {
            throw new RuntimeException("NettyClient 连接失败", e);
        }
    }

    /**
     * 发送请求并等待响应（同步阻塞）
     */
    public Response send(Request request) {
        if (channel == null || !channel.isActive()) {
            connect();
        }

        CompletableFuture<Response> future = new CompletableFuture<>();
        pendingRequests.put(request.getId(), future);

        channel.writeAndFlush(request).addListener((ChannelFutureListener) f -> {
            if (!f.isSuccess()) {
                future.completeExceptionally(f.cause());
                pendingRequests.remove(request.getId());
            }
        });

        try {
            return future.get(); // 阻塞等待响应
        } catch (Exception e) {
            pendingRequests.remove(request.getId());
            throw new RuntimeException("请求失败: " + e.getMessage(), e);
        }
    }

    public void close() {
        if (channel != null) channel.close();
        if (group != null) group.shutdownGracefully();
        System.out.println("[NettyClient] 已关闭");
    }

    // ==================== Netty Handlers ====================

    /**
     * 编解码处理器（与服务端共用逻辑）
     * 编码：Request → length prefix + body bytes
     * 解码：length prefix + body bytes → Response
     */
    @ChannelHandler.Sharable
    static class NettyCodecHandler extends ChannelDuplexHandler {

        private final DubboCodec codec;

        NettyCodecHandler(DubboCodec codec) { this.codec = codec; }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ByteBuf buf = (ByteBuf) msg;
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            // 解码为 Response
            Response response = (Response) codec.decode(bytes);
            ctx.fireChannelRead(response);
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg,
                          ChannelPromise promise) throws Exception {
            // 编码 Request
            byte[] bytes = codec.encode((Request) msg);
            ByteBuf buf = ctx.alloc().buffer(bytes.length + 4);
            buf.writeInt(bytes.length);
            buf.writeBytes(bytes);
            ctx.write(buf, promise);
        }
    }

    /**
     * 客户端业务处理器
     */
    @ChannelHandler.Sharable
    static class NettyClientHandler extends SimpleChannelInboundHandler<Response> {

        private final Map<Long, CompletableFuture<Response>> pendingRequests;

        NettyClientHandler(Map<Long, CompletableFuture<Response>> pending) {
            this.pendingRequests = pending;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Response response) {
            CompletableFuture<Response> future = pendingRequests.remove(response.getId());
            if (future != null) {
                future.complete(response); // 唤醒 send() 中阻塞的线程
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.err.println("[NettyClient] 异常: " + cause.getMessage());
            ctx.close();
        }
    }
}
