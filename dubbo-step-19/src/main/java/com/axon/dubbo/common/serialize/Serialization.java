package com.axon.dubbo.common.serialize;

import com.axon.dubbo.common.extension.SPI;
import java.io.IOException;

/**
 * 序列化接口 —— @SPI 扩展点
 *
 * @SPI("java") 表示默认使用 JDK 序列化
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
@SPI("java")
public interface Serialization {
    byte[] serialize(Object obj) throws IOException;
    <T> T deserialize(byte[] bytes, Class<T> clazz) throws IOException;
}
