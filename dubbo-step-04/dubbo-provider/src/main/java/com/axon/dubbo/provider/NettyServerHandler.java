package com.axon.dubbo.provider;

import com.axon.dubbo.api.HelloService;
import com.axon.dubbo.common.RpcRequest;
import com.axon.dubbo.common.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.lang.reflect.Method;

/**
 * @author：liupengfei
 * @date：2025/5/26
 * @description：
 */
public class NettyServerHandler extends ChannelInboundHandlerAdapter {

    private final HelloService service;

    public NettyServerHandler(HelloService service) {
        this.service = service;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        RpcRequest request = (RpcRequest) msg;
        RpcResponse response = new RpcResponse();

        try {
            Method method = service.getClass().getMethod(request.getMethodName(), request.getParameterTypes());
            Object result = method.invoke(service, request.getParameters());
            response.setResult(result);
        } catch (Exception e) {
            e.printStackTrace();
            response.setResult("执行异常: " + e.getMessage());
        }

        //ctx.writeAndFlush(response);
        ctx.writeAndFlush(response).addListener(future -> {
            if (!future.isSuccess()) {
                future.cause().printStackTrace();
            }
        });
    }
}
