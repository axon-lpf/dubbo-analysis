package com.axon.dubbo.common.serialize;

import java.io.IOException;

/**
 * 序列化接口（SPI 扩展点）
 *
 * 定义对象与字节数组之间的转换契约。
 * 所有序列化实现（JDK、Hessian2、Fastjson、Kryo 等）都必须实现此接口。
 *
 * 在 Dubbo 中，序列化是通过 SPI 机制动态加载的，通过协议头的序列化标志位
 * 来决定使用哪种序列化方式。本步骤先使用最简单的 JDK 原生序列化。
 *
 * 对应官方源码：org.apache.dubbo.common.serialize.Serialization
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public interface Serialization {

    /**
     * 将 Java 对象序列化为字节数组
     *
     * 序列化解决了"对象如何离开 JVM"的问题：
     * Java 对象存在于堆内存中，只有将对象转换为字节序列，
     * 才能通过网络传输或持久化到磁盘。
     *
     * @param obj 待序列化的对象
     * @return 字节数组
     * @throws IOException 序列化失败时抛出
     */
    byte[] serialize(Object obj) throws IOException;

    /**
     * 将字节数组反序列化为 Java 对象
     *
     * 反序列化解决了"字节如何变回对象"的问题：
     * 接收方拿到字节数组后，根据类信息重建原始对象。
     *
     * @param bytes 字节数组
     * @param clazz 目标类型
     * @param <T>   泛型类型
     * @return 反序列化后的对象
     * @throws IOException 反序列化失败时抛出
     */
    <T> T deserialize(byte[] bytes, Class<T> clazz) throws IOException;
}
