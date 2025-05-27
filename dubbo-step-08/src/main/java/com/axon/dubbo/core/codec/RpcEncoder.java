package com.axon.dubbo.core.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;

/**
 * @author：liupengfei
 * @date：2025/5/27
 * @description：
 */
public class RpcEncoder extends MessageToByteEncoder<Object> {

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {

            oos.writeObject(msg);
            byte[] data = bos.toByteArray();

            out.writeInt(data.length);  // 先写长度
            out.writeBytes(data);       // 再写数据
        }
    }
}
