package com.axon.dubbo.common.serialize.java;

import com.axon.dubbo.common.serialize.Serialization;
import java.io.*;

public class JavaSerialization implements Serialization {
    @Override
    public byte[] serialize(Object obj) throws IOException {
        if (obj == null) throw new IllegalArgumentException("不能为null");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) { oos.writeObject(obj); oos.flush(); }
        return baos.toByteArray();
    }

    @Override @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] bytes, Class<T> clazz) throws IOException {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("不能为空");
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            Object obj = ois.readObject();
            if (!clazz.isInstance(obj)) throw new IOException("类型不匹配");
            return (T) obj;
        } catch (ClassNotFoundException e) { throw new IOException("找不到类", e); }
    }
}
