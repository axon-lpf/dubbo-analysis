package com.axon.dubbo.common.serialize.kryo;

import com.axon.dubbo.common.serialize.Serialization;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.io.*;

/**
 * Kryo 序列化实现
 *
 * 优点：体积最小、速度最快（编译期生成序列化代码）
 * 缺点：线程不安全（需要 ThreadLocal 或 Kryo 池）
 *       需要提前注册类（或使用非安全模式）
 */
public class KryoSerialization implements Serialization {

    /** Kryo 非线程安全 → ThreadLocal 隔离 */
    private static final ThreadLocal<Kryo> KRYO = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false); // 不强制类注册
        return kryo;
    });

    @Override
    public byte[] serialize(Object obj) throws IOException {
        if (obj == null) throw new IllegalArgumentException("不能为null");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Output output = new Output(baos);
        KRYO.get().writeClassAndObject(output, obj);
        output.flush();
        return baos.toByteArray();
    }

    @Override @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] bytes, Class<T> clazz) throws IOException {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("不能为空");
        Input input = new Input(new ByteArrayInputStream(bytes));
        return (T) KRYO.get().readClassAndObject(input);
    }
}
