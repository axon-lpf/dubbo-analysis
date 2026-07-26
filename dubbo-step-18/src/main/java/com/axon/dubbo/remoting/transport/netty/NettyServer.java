package com.axon.dubbo.remoting.transport.netty;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.remoting.exchange.DubboCodec;
import com.axon.dubbo.remoting.transport.socket.Request;
import com.axon.dubbo.remoting.transport.socket.Response;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

import java.util.function.Function;

/**
 * Netty 服务端
 *
 * 用 Netty NIO 替换 BIO ServerSocket。
 *
 * Netty 线程模型（Reactor）：
 *   bossGroup (1 线程)  → 接收连接
 *   workerGroup (N 线程) → 处理 I/O 读写
 *
 * Pipeline 结构：
 *   LengthFieldBasedFrameDecoder  ← 解决粘包/拆包
 *     → Encoder (length prefix + body)
 *       → NettyServerHandler (业务处理)
 *
 * 对比 BIO：一个连接 = 一个线程 → NIO：一个线程处理多个连接
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class NettyServer {

    private final int port;
    private final DubboCodec codec;
    private final Function<Request, Response> requestHandler;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public NettyServer(int port, DubboCodec codec,
                       Function<Request, Response> requestHandler) {
        this.port = port;
        this.codec = codec;
        this.requestHandler = requestHandler;
    }

    /**
     * 启动 Netty 服务端
     */
    public void start() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    // 1. 帧解码器（4字节长度前缀 + 变长数据体）
                                    .addLast(new LengthFieldBasedFrameDecoder(
                                            1024 * 1024, 0, 4, 0, 4))
                                    // 2. Netty → Dubbo 消息编解码器
                                    .addLast(new NettyCodecHandler(codec))
                                    // 3. 业务处理器
                                    .addLast(new NettyServerHandler(requestHandler));
                        }
                    });

            ChannelFuture future = bootstrap.bind(port).sync();
            serverChannel = future.channel();
            System.out.println("[NettyServer] NIO 服务端启动成功，端口: " + port);

        } catch (Exception e) {
            throw new RuntimeException("NettyServer 启动失败", e);
        }
    }

    public void close() {
        if (serverChannel != null) serverChannel.close();
        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
        System.out.println("[NettyServer] 已关闭");
    }

    // ==================== Netty Handler ====================

    /**
     * 编解码处理器
     *
     * 编码：Response → length prefix + body bytes
     * 解码：length prefix + body bytes → Request
     */
    @ChannelHandler.Sharable
    static class NettyCodecHandler extends ChannelDuplexHandler {

        private final DubboCodec codec;

        NettyCodecHandler(DubboCodec codec) { this.codec = codec; }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            // 收到整帧消息（LengthFieldBasedFrameDecoder 已处理好粘包/拆包）
            ByteBuf buf = (ByteBuf) msg;
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            // DubboCodec 解码
            Request request = (Request) codec.decode(bytes);
            // 传递给下一个 Handler
            ctx.fireChannelRead(request);
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg,
                          ChannelPromise promise) throws Exception {
            // 编码 Response
            byte[] bytes = codec.encode((Response) msg);
            ByteBuf buf = ctx.alloc().buffer(bytes.length + 4);
            buf.writeInt(bytes.length);  // 4 字节长度前缀
            buf.writeBytes(bytes);       // 数据体
            ctx.write(buf, promise);
        }
    }

    /**
     * 业务处理器
     */
    @ChannelHandler.Sharable
    static class NettyServerHandler extends SimpleChannelInboundHandler<Request> {

        private final Function<Request, Response> requestHandler;

        NettyServerHandler(Function<Request, Response> handler) {
            this.requestHandler = handler;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Request request) {
            Response response = requestHandler.apply(request);
            ctx.writeAndFlush(response);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.err.println("[NettyServer] 异常: " + cause.getMessage());
            ctx.close();
        }
    }
}
