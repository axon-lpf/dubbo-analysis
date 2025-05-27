package com.axon.dubbo.consumer;

import com.axon.dubbo.common.RpcRequest;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author：liupengfei
 * @date：2025/5/26
 * @description：
 */
// consumer/RpcClient.java
public class RpcNettyClient {
    private Object response;

/*    public Object send(RpcRequest request, String host, int port) throws InterruptedException {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            RpcClientHandler handler = new RpcClientHandler();

            bootstrap.group(group).channel(NioSocketChannel.class).handler(new ChannelInitializer<Channel>() {
                protected void initChannel(Channel ch) {
                    ChannelPipeline pipeline = ch.pipeline();
                    pipeline.addLast(new ObjectEncoder());
                    pipeline.addLast(new ObjectDecoder(1024 * 1024, ClassResolvers.cacheDisabled(null)));
                    pipeline.addLast(handler);
                }
            });

            ChannelFuture future = bootstrap.connect(host, port).sync();
            future.channel().writeAndFlush(request).sync();
            future.channel().closeFuture().sync();

            return handler.getResponse();
        } finally {
            group.shutdownGracefully();
        }
    }*/

    public Object send(RpcRequest request, String host, int port) throws InterruptedException {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            RpcClientHandler handler = new RpcClientHandler();

            bootstrap.group(group).channel(NioSocketChannel.class).handler(new ChannelInitializer<Channel>() {
                protected void initChannel(Channel ch) {
                    ChannelPipeline pipeline = ch.pipeline();
                    pipeline.addLast(new ObjectEncoder());
                    pipeline.addLast(new ObjectDecoder(1024 * 1024, ClassResolvers.cacheDisabled(null)));
                    pipeline.addLast(handler);
                }
            });

            // 连接服务器
            ChannelFuture connectFuture = bootstrap.connect(host, port).sync();
            // 发送请求
            connectFuture.channel().writeAndFlush(request);

            // 等待响应，设置超时时间为5秒
            Object response = handler.getResponseFuture()
                                     .get(5, TimeUnit.SECONDS);
            // 获取响应后关闭连接
            connectFuture.channel().close();
            return response;
        } catch (ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        } finally {
            group.shutdownGracefully();
        }
    }
}
