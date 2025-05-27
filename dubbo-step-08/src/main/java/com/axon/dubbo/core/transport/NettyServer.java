package com.axon.dubbo.core.transport;

import com.axon.dubbo.core.codec.RpcDecoder;
import com.axon.dubbo.core.codec.RpcEncoder;
import com.axon.dubbo.core.codec.RpcRequest;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.*;

import java.util.concurrent.CompletableFuture;

/**
 * @author：liupengfei
 * @date：2025/5/27
 * @description：
 */

public class NettyServer {

    private static Channel serverChannel;

    private static EventLoopGroup bossGroup;

    private static EventLoopGroup workerGroup;

    public static CompletableFuture<Void> start(int port) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                 .childHandler(new ChannelInitializer<SocketChannel>() {
                     @Override
                     protected void initChannel(SocketChannel ch) throws Exception {
                         ChannelPipeline pipeline = ch.pipeline();
                         pipeline.addLast(new RpcDecoder(RpcRequest.class));
                         pipeline.addLast(new RpcEncoder());
                         pipeline.addLast(new NettyServerHandler());
                     }
                 }).option(ChannelOption.SO_BACKLOG, 128).childOption(ChannelOption.SO_KEEPALIVE, true);

        bootstrap.bind(port).addListener((ChannelFutureListener) channelFuture -> {
            if (channelFuture.isSuccess()) {
                serverChannel = channelFuture.channel();
                System.out.println("Netty服务端异步启动成功，端口：" + port);
                future.complete(null);
            } else {
                System.err.println("Netty服务端启动失败");
                future.completeExceptionally(channelFuture.cause());
                shutdown();
            }
        });

        return future;
    }

    public static CompletableFuture<Void> shutdown() {
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (serverChannel != null) {
            serverChannel.close().addListener((ChannelFutureListener) channelFuture -> {
                if (bossGroup != null) {
                    bossGroup.shutdownGracefully();
                }
                if (workerGroup != null) {
                    workerGroup.shutdownGracefully();
                }
                future.complete(null);
            });
        } else {
            if (bossGroup != null) {
                bossGroup.shutdownGracefully();
            }
            if (workerGroup != null) {
                workerGroup.shutdownGracefully();
            }
            future.complete(null);
        }

        return future;
    }
}
