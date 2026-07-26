package com.axon.dubbo.remoting.exchange;

import com.axon.dubbo.common.Constants;
import com.axon.dubbo.common.extension.ExtensionLoader;
import com.axon.dubbo.common.serialize.Serialization;
import com.axon.dubbo.remoting.Codec;
import com.axon.dubbo.remoting.transport.socket.Request;
import com.axon.dubbo.remoting.transport.socket.Response;

import java.io.*;

/**
 * Dubbo 协议编解码器（Step 18 升级版）
 *
 * 升级：通过 SPI + 协议头标志位动态选择序列化方式
 *
 * 协议头 Flag 字节低 3 位 = 序列化 ID:
 *   0 = JDK, 1 = Hessian2, 2 = Fastjson, 3 = Kryo
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class DubboCodec implements Codec {

    private static final String[] SERIALIZATION_NAMES = {"java", "hessian2", "fastjson", "kryo"};

    private volatile byte serializationId = Constants.SERIALIZATION_JDK;

    public DubboCodec() {}

    public DubboCodec(byte serializationId) {
        this.serializationId = serializationId;
    }

    public void setSerializationId(byte id) { this.serializationId = id; }

    private Serialization getSerialization(byte id) {
        if (id < 0 || id >= SERIALIZATION_NAMES.length) id = 0;
        return ExtensionLoader.getExtensionLoader(Serialization.class)
                .getExtension(SERIALIZATION_NAMES[id]);
    }

    @Override
    public byte[] encode(Object message) throws IOException {
        if (message instanceof Request) return encodeRequest((Request) message);
        if (message instanceof Response) return encodeResponse((Response) message);
        throw new IOException("不支持的消息类型");
    }

    @Override
    public Object decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < Constants.HEADER_LENGTH)
            throw new IOException("消息太短");

        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes));

        short magic = dis.readShort();
        if (magic != Constants.MAGIC) throw new IOException("魔数校验失败");

        byte flag = dis.readByte();
        boolean isResponse = (flag & Constants.FLAG_RESPONSE) != 0;
        byte serId = (byte) (flag & 0x07);
        dis.readByte(); // status
        dis.readLong(); // requestId
        int bodyLength = dis.readInt();

        byte[] body = new byte[bodyLength];
        dis.readFully(body);

        // Step 18: 根据协议头标志位，SPI 动态加载序列化实现
        Serialization ser = getSerialization(serId);
        return isResponse ? ser.deserialize(body, Response.class)
                : ser.deserialize(body, Request.class);
    }

    private byte[] encodeRequest(Request request) throws IOException {
        Serialization ser = getSerialization(serializationId);
        byte[] body = ser.serialize(request);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeShort(Constants.MAGIC);
        dos.writeByte(serializationId); // Flag: 序列化ID
        dos.writeByte((byte) 0);
        dos.writeLong(request.getId());
        dos.writeInt(body.length);
        dos.write(body);
        dos.flush();
        return baos.toByteArray();
    }

    private byte[] encodeResponse(Response response) throws IOException {
        Serialization ser = getSerialization(serializationId);
        byte[] body = ser.serialize(response);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeShort(Constants.MAGIC);
        dos.writeByte((byte) (Constants.FLAG_RESPONSE | serializationId));
        dos.writeByte(response.isSuccess() ? Constants.RESPONSE_OK : Constants.RESPONSE_ERROR);
        dos.writeLong(response.getId());
        dos.writeInt(body.length);
        dos.write(body);
        dos.flush();
        return baos.toByteArray();
    }
}
