package com.axon.dubbo.core.transport;

import com.axon.dubbo.core.codec.RpcRequest;
import com.axon.dubbo.core.codec.RpcResponse;
import com.axon.dubbo.provider.ServiceRepository;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * @author：liupengfei
 * @date：2025/5/27
 * @description：
 */
public class NettyServerHandler extends SimpleChannelInboundHandler<RpcRequest> {


    public NettyServerHandler() {
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcRequest request) throws Exception {
        RpcResponse response = new RpcResponse();

        try {
            Object serviceBean = ServiceRepository.getService(request.getInterfaceName());
            if (serviceBean == null) {
                throw new RuntimeException("未找到服务: " + request.getInterfaceName());
            }
            Method method = serviceBean.getClass().getMethod(request.getMethodName(), request.getParamTypes());
            Object result = method.invoke(serviceBean, request.getParameters());
            response.setResult(result);
        } catch (Exception e) {
            response.setException(e);
        }

        ctx.writeAndFlush(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}