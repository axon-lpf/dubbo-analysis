package com.axon.dubbo.core.transport;

import com.axon.dubbo.core.codec.RpcDecoder;
import com.axon.dubbo.core.codec.RpcEncoder;
import com.axon.dubbo.core.codec.RpcRequest;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import java.util.Map;


import java.util.HashMap;

/**
 * @author：liupengfei
 * @date：2025/5/27
 * @description：
 */
public class NettyServer {


    private final int port;
    private final Map<String, Object> serviceRegistry;

    public NettyServer(int port) {
        this.port = port;
        this.serviceRegistry = new HashMap<>();
    }

    // 注册服务
    public void registerService(String interfaceName, Object serviceBean) {
        serviceRegistry.put(interfaceName, serviceBean);
    }

    public void start() throws InterruptedException {
        EventLoopGroup bossGroup = new NioEventLoopGroup();  // 接收连接线程组
        EventLoopGroup workerGroup = new NioEventLoopGroup(); // 处理读写线程组

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                     .channel(NioServerSocketChannel.class)
                     .childHandler(new ChannelInitializer<SocketChannel>() {
                         @Override
                         protected void initChannel(SocketChannel ch) throws Exception {
                             ChannelPipeline pipeline = ch.pipeline();

                             pipeline.addLast(new RpcDecoder(RpcRequest.class));
                             pipeline.addLast(new RpcEncoder());
                             pipeline.addLast(new NettyServerHandler(serviceRegistry));
                         }
                     })
                     .option(ChannelOption.SO_BACKLOG, 128)
                     .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture f = bootstrap.bind(port).sync();
            System.out.println("Netty服务端启动，端口：" + port);
            f.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
}
