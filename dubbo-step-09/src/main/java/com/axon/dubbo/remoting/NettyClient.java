package com.axon.dubbo.remoting;

import com.axon.dubbo.core.codec.RpcDecoder;
import com.axon.dubbo.core.codec.RpcEncoder;
import com.axon.dubbo.core.codec.RpcRequest;
import com.axon.dubbo.core.codec.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.concurrent.ArrayBlockingQueue;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;


/**
 * @author：liupengfei
 * @date：2025/5/27
 * @description：
 */
public class NettyClient {

    public static RpcResponse sendRequest(String address, RpcRequest request) {
        String[] ipPort = address.split(":");
        String host = ipPort[0];
        int port = Integer.parseInt(ipPort[1]);

        EventLoopGroup group = new NioEventLoopGroup();
        ArrayBlockingQueue<RpcResponse> responseQueue = new ArrayBlockingQueue<>(1);

        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                     .channel(NioSocketChannel.class)
                     .handler(new ChannelInitializer<Channel>() {
                         @Override
                         protected void initChannel(Channel ch) {
                             ChannelPipeline p = ch.pipeline();
                             p.addLast(new RpcEncoder());
                             p.addLast(new RpcDecoder(RpcResponse.class));
                             p.addLast(new SimpleChannelInboundHandler<RpcResponse>() {
                                 @Override
                                 protected void channelRead0(ChannelHandlerContext ctx, RpcResponse msg) {
                                     responseQueue.offer(msg);
                                 }
                             });
                         }
                     });

            ChannelFuture future = bootstrap.connect(host, port).sync();
            future.channel().writeAndFlush(request).sync();
            return responseQueue.take();

        } catch (Exception e) {
            throw new RuntimeException("客户端调用失败", e);
        } finally {
            group.shutdownGracefully();
        }
    }
}
