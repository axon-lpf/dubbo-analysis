package com.axon.dubbo;

import com.axon.dubbo.api.HelloService;
import com.axon.dubbo.provider.HelloServiceImpl;
import com.axon.dubbo.provider.NettyServerHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;

/**
 * @author：liupengfei
 * @date：2025/5/26
 * @description：
 */
public class ServerMain {
    public static void main(String[] args) throws InterruptedException {
        HelloService helloService = new HelloServiceImpl();

        EventLoopGroup boss = new NioEventLoopGroup();
        EventLoopGroup worker = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(boss, worker)
                     .channel(NioServerSocketChannel.class)
                     .childHandler(new ChannelInitializer<SocketChannel>() {
                         protected void initChannel(SocketChannel ch) {
                             ChannelPipeline p = ch.pipeline();
                             p.addLast(new ObjectDecoder(1024 * 1024, ClassResolvers.cacheDisabled(null)));
                             p.addLast(new ObjectEncoder());
                             p.addLast(new NettyServerHandler(helloService));
                         }
                     });

            ChannelFuture future = bootstrap.bind("localhost",8080).sync();
            System.out.println("Netty RPC Server 启动成功，端口 8080");
            future.channel().closeFuture().sync();
        } finally {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }
}