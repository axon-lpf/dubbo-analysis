package com.axon.dubbo.common.serialize.hessian2;

import com.axon.dubbo.common.serialize.Serialization;
import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.Hessian2Output;

import java.io.*;

/**
 * Hessian2 序列化实现
 *
 * 优点：二进制协议、体积小、跨语言、速度快
 * Dubbo 默认序列化方式（官方推荐）
 */
public class Hessian2Serialization implements Serialization {

    @Override
    public byte[] serialize(Object obj) throws IOException {
        if (obj == null) throw new IllegalArgumentException("不能为null");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Hessian2Output out = new Hessian2Output(baos);
        out.writeObject(obj);
        out.flush();
        return baos.toByteArray();
    }

    @Override @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] bytes, Class<T> clazz) throws IOException {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("不能为空");
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        Hessian2Input in = new Hessian2Input(bais);
        try {
            return (T) in.readObject();
        } catch (Exception e) {
            throw new IOException("Hessian2 反序列化失败: " + e.getMessage(), e);
        }
    }
}
