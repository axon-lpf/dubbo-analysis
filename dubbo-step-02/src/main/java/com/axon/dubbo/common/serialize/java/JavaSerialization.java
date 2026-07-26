package com.axon.dubbo.common.serialize.java;

import com.axon.dubbo.common.serialize.Serialization;

import java.io.*;

/**
 * JDK 原生序列化实现
 *
 * 基于 Java 内置的 ObjectOutputStream / ObjectInputStream 实现。
 *
 * 优点：
 * - JDK 原生支持，无需第三方依赖
 * - 使用简单，所有实现了 Serializable 接口的对象都能序列化
 *
 * 缺点：
 * - 序列化后的字节体积大（携带了类的完整描述信息）
 * - 性能较差（大量反射操作）
 * - 跨语言支持不好（只能 Java 使用）
 * - serialVersionUID 管理麻烦
 * - 安全性差（反序列化漏洞）
 *
 * 正是由于这些缺点，Dubbo 默认使用 Hessian2 序列化，
 * 并支持 Fastjson、Kryo、Protobuf 等多种序列化方式。
 * 我们将在 Step 18 中实现多序列化扩展。
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class JavaSerialization implements Serialization {

    @Override
    public byte[] serialize(Object obj) throws IOException {
        // 1. 参数校验
        if (obj == null) {
            throw new IllegalArgumentException("序列化对象不能为 null");
        }

        // 2. 使用 ByteArrayOutputStream 作为字节缓冲区
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // 3. 使用 ObjectOutputStream 将对象写入字节流
        //    ObjectOutputStream 内部会：
        //    a) 写入类的元数据（类名、字段名、字段类型等）
        //    b) 递归写入所有字段的值
        //    c) 处理引用关系（同一个对象不会重复写入）
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
            oos.flush();
        }

        return baos.toByteArray();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] bytes, Class<T> clazz) throws IOException {
        // 1. 参数校验
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("反序列化的字节数组不能为空");
        }

        // 2. 使用 ByteArrayInputStream 包装字节数组
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);

        // 3. 使用 ObjectInputStream 从字节流中读取对象
        //    ObjectInputStream 内部会：
        //    a) 读取类的元数据
        //    b) 通过反射创建对象实例
        //    c) 递归读取所有字段的值并设置
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            Object obj = ois.readObject();

            // 4. 类型检查
            if (!clazz.isInstance(obj)) {
                throw new IOException(
                        "反序列化类型不匹配: 期望 " + clazz.getName()
                                + ", 实际 " + obj.getClass().getName());
            }

            return (T) obj;

        } catch (ClassNotFoundException e) {
            // ClassNotFoundException 表明接收方缺少对应的类定义
            // 在 RPC 场景中，通常是因为 consumer 和 provider 的 jar 包版本不一致
            throw new IOException("反序列化失败：找不到类定义，请检查服务双方的版本是否一致", e);
        }
    }
}
