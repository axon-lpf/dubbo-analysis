package com.axon.dubbo.core.transport;

import com.axon.dubbo.core.codec.RpcDecoder;
import com.axon.dubbo.core.codec.RpcEncoder;
import com.axon.dubbo.core.codec.RpcRequest;
import com.axon.dubbo.core.codec.RpcResponse;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.concurrent.CompletableFuture;
import io.netty.bootstrap.Bootstrap;


/**
 * @author：liupengfei
 * @date：2025/5/27
 * @description：
 */
public class NettyClient {

    private final String host;
    private final int port;

    private Channel channel;
    private final CompletableFuture<RpcResponse> responseFuture = new CompletableFuture<>();

    public NettyClient(String host, int port) throws InterruptedException {
        this.host = host;
        this.port = port;
        connect();
    }

    private void connect() throws InterruptedException {
        EventLoopGroup group = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();

        bootstrap.group(group)
                 .channel(NioSocketChannel.class)
                 .handler(new ChannelInitializer<Channel>() {
                     @Override
                     protected void initChannel(Channel ch) throws Exception {
                         ch.pipeline().addLast(new RpcEncoder());
                         ch.pipeline().addLast(new RpcDecoder(RpcResponse.class));
                         ch.pipeline().addLast(new NettyClientHandler(responseFuture));
                     }
                 });

        ChannelFuture future = bootstrap.connect(host, port).sync();
        channel = future.channel();
    }

    public RpcResponse sendRequest(RpcRequest request) throws Exception {
        channel.writeAndFlush(request).sync();
        return responseFuture.get();
    }
}