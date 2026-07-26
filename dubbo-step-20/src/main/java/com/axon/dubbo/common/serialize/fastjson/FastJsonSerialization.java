package com.axon.dubbo.common.serialize.fastjson;

import com.alibaba.fastjson.JSON;
import com.axon.dubbo.common.serialize.Serialization;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Fastjson 序列化实现
 *
 * 优点：文本可读（JSON）、速度快、中文友好
 * 缺点：体积比二进制大
 */
public class FastJsonSerialization implements Serialization {

    @Override
    public byte[] serialize(Object obj) throws IOException {
        if (obj == null) throw new IllegalArgumentException("不能为null");
        String json = JSON.toJSONString(obj);
        return json.getBytes(StandardCharsets.UTF_8);
    }

    @Override @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] bytes, Class<T> clazz) throws IOException {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("不能为空");
        String json = new String(bytes, StandardCharsets.UTF_8);
        return JSON.parseObject(json, clazz);
    }
}
