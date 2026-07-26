package com.axon.dubbo.common.serialize.java;

import com.axon.dubbo.common.serialize.Serialization;

import java.io.*;

/**
 * JDK 原生序列化实现
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class JavaSerialization implements Serialization {

    @Override
    public byte[] serialize(Object obj) throws IOException {
        if (obj == null) {
            throw new IllegalArgumentException("序列化对象不能为 null");
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
            oos.flush();
        }
        return baos.toByteArray();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] bytes, Class<T> clazz) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("反序列化的字节数组不能为空");
        }
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            Object obj = ois.readObject();
            if (!clazz.isInstance(obj)) {
                throw new IOException("反序列化类型不匹配: 期望 " + clazz.getName()
                        + ", 实际 " + obj.getClass().getName());
            }
            return (T) obj;
        } catch (ClassNotFoundException e) {
            throw new IOException("反序列化失败：找不到类定义", e);
        }
    }
}
