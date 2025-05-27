package com.axon.dubbo.core.transport;

import com.axon.dubbo.core.codec.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.concurrent.CompletableFuture;

/**
 * @author：liupengfei
 * @date：2025/5/27
 * @description：
 */
public class NettyClientHandler extends SimpleChannelInboundHandler<RpcResponse> {

    private final CompletableFuture<RpcResponse> responseFuture;

    public NettyClientHandler(CompletableFuture<RpcResponse> responseFuture) {
        this.responseFuture = responseFuture;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcResponse msg) throws Exception {
        responseFuture.complete(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        responseFuture.completeExceptionally(cause);
        ctx.close();
    }
}