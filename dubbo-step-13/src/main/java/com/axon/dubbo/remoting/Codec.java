package com.axon.dubbo.remoting;

import java.io.IOException;

/**
 * 编解码器接口
 *
 * 负责将 Java 对象编码为网络传输的字节流，以及从字节流解码还原为 Java 对象。
 *
 * 对应官方源码：org.apache.dubbo.remoting.Codec2
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public interface Codec {

    /**
     * 编码：Java 对象 → 带协议头的字节数组
     *
     * @param message 待编码的消息（Request 或 Response）
     * @return 完整的协议消息字节数组（协议头 + 数据体）
     * @throws IOException 编码失败
     */
    byte[] encode(Object message) throws IOException;

    /**
     * 解码：带协议头的字节数组 → Java 对象
     *
     * @param bytes 完整的协议消息字节数组
     * @return 解码后的消息对象（Request 或 Response）
     * @throws IOException 解码失败
     */
    Object decode(byte[] bytes) throws IOException;
}
