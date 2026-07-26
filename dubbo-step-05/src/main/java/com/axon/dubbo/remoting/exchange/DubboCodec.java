package com.axon.dubbo.remoting.exchange;

import com.axon.dubbo.common.Constants;
import com.axon.dubbo.common.serialize.Serialization;
import com.axon.dubbo.common.serialize.java.JavaSerialization;
import com.axon.dubbo.remoting.Codec;
import com.axon.dubbo.remoting.transport.socket.Request;
import com.axon.dubbo.remoting.transport.socket.Response;

import java.io.*;

/**
 * Dubbo 协议编解码器
 *
 * 实现 Dubbo 协议的编码和解码。
 *
 * Dubbo 协议消息格式（共 16 字节协议头 + 变长数据体）：
 *
 * ┌───────┬───────┬───────┬───────┬───────────────┬───────────────┬───────┐
 * │ 0-1   │ 2     │ 3     │ 4-11  │ 12-15         │ 16+           │       │
 * │ Magic │ Flag  │Status │Req ID │ Body Length   │ Body          │       │
 * │0xdabb │       │       │(long) │ (int)         │ (序列化字节)    │       │
 * └───────┴───────┴───────┴───────┴───────────────┴───────────────┴───────┘
 *
 * 协议头各字段说明：
 * - Magic (2B):  魔数 0xdabb，用于快速识别 Dubbo 协议消息
 * - Flag (1B):   标志位，高 3 位用于区分请求/响应/单向
 * - Status (1B): 响应状态码（20=OK, 30=CLIENT_ERROR, 31=SERVER_ERROR）
 * - Req ID (8B): 请求 ID，用于异步匹配请求和响应
 * - Length (4B): 数据体长度（字节数）
 *
 * 对应官方源码：org.apache.dubbo.remoting.exchange.codec.ExchangeCodec
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class DubboCodec implements Codec {

    private final Serialization serialization;

    public DubboCodec() {
        this.serialization = new JavaSerialization();
    }

    public DubboCodec(Serialization serialization) {
        this.serialization = serialization;
    }

    /**
     * 编码：消息对象 → 协议消息字节数组
     */
    @Override
    public byte[] encode(Object message) throws IOException {
        if (message instanceof Request) {
            return encodeRequest((Request) message);
        } else if (message instanceof Response) {
            return encodeResponse((Response) message);
        }
        throw new IOException("不支持的消息类型: " + message.getClass().getName());
    }

    /**
     * 解码：协议消息字节数组 → 消息对象
     */
    @Override
    public Object decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < Constants.HEADER_LENGTH) {
            throw new IOException("消息太短，至少需要" + Constants.HEADER_LENGTH + "字节");
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        DataInputStream dis = new DataInputStream(bais);

        // 1. 读取并校验魔数
        short magic = dis.readShort();
        if (magic != Constants.MAGIC) {
            throw new IOException("魔数校验失败: 期望 0x"
                    + Integer.toHexString(Constants.MAGIC & 0xFFFF)
                    + ", 实际 0x" + Integer.toHexString(magic & 0xFFFF));
        }

        // 2. 读取标志位
        byte flag = dis.readByte();
        boolean isResponse = (flag & Constants.FLAG_RESPONSE) != 0;

        // 3. 读取状态码（响应时有效）
        byte status = dis.readByte();

        // 4. 读取请求 ID
        long requestId = dis.readLong();

        // 5. 读取数据体长度
        int bodyLength = dis.readInt();

        // 6. 读取数据体
        if (bodyLength > bytes.length - Constants.HEADER_LENGTH) {
            throw new IOException("数据体长度异常: " + bodyLength);
        }
        byte[] body = new byte[bodyLength];
        dis.readFully(body);

        // 7. 反序列化数据体
        if (isResponse) {
            Response response = serialization.deserialize(body, Response.class);
            return response;
        } else {
            Request request = serialization.deserialize(body, Request.class);
            return request;
        }
    }

    /**
     * 编码请求消息
     */
    private byte[] encodeRequest(Request request) throws IOException {
        // 1. 序列化请求体
        byte[] body = serialization.serialize(request);

        // 2. 构建协议头 + 数据体
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        // Magic (2 bytes)
        dos.writeShort(Constants.MAGIC);
        // Flag (1 byte): 请求 + 双向
        dos.writeByte(Constants.FLAG_REQUEST);
        // Status (1 byte): 请求时无意义
        dos.writeByte((byte) 0);
        // Request ID (8 bytes)
        dos.writeLong(request.getId());
        // Body Length (4 bytes)
        dos.writeInt(body.length);
        // Body (N bytes)
        dos.write(body);
        dos.flush();

        return baos.toByteArray();
    }

    /**
     * 编码响应消息
     */
    private byte[] encodeResponse(Response response) throws IOException {
        // 1. 序列化响应体
        byte[] body = serialization.serialize(response);

        // 2. 构建协议头 + 数据体
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        // Magic (2 bytes)
        dos.writeShort(Constants.MAGIC);
        // Flag (1 byte): 响应
        dos.writeByte(Constants.FLAG_RESPONSE);
        // Status (1 byte)
        dos.writeByte(response.isSuccess() ? Constants.RESPONSE_OK : Constants.RESPONSE_ERROR);
        // Request ID (8 bytes)
        dos.writeLong(response.getId());
        // Body Length (4 bytes)
        dos.writeInt(body.length);
        // Body (N bytes)
        dos.write(body);
        dos.flush();

        return baos.toByteArray();
    }
}
