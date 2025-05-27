package com.axon.dubbo.consumer;

import com.axon.dubbo.common.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.concurrent.CompletableFuture;

/**
 * @author：liupengfei
 * @date：2025/5/26
 * @description：
 */
public class RpcClientHandler extends ChannelInboundHandlerAdapter {

    private CompletableFuture<Object> responseFuture = new CompletableFuture<>();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        RpcResponse rpcResponse = (RpcResponse) msg;
        responseFuture.complete(rpcResponse.getResult());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    public CompletableFuture<Object> getResponseFuture() {
        return responseFuture;
    }
}
