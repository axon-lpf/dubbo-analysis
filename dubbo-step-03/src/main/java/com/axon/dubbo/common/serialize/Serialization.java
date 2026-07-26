package com.axon.dubbo.common.serialize;

import java.io.IOException;

/**
 * 序列化接口 —— 定义对象与字节数组之间的转换契约
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public interface Serialization {
    byte[] serialize(Object obj) throws IOException;
    <T> T deserialize(byte[] bytes, Class<T> clazz) throws IOException;
}
