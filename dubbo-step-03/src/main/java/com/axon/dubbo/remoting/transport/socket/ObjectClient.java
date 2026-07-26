package com.axon.dubbo.remoting.transport.socket;

import com.axon.dubbo.common.serialize.Serialization;
import com.axon.dubbo.common.serialize.java.JavaSerialization;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

/**
 * 对象序列化客户端
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class ObjectClient {

    private final String host;
    private final int port;
    private final Serialization serialization;

    public ObjectClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.serialization = new JavaSerialization();
    }

    public ObjectClient(String host, int port, Serialization serialization) {
        this.host = host;
        this.port = port;
        this.serialization = serialization;
    }

    /**
     * 发送请求并获取响应
     */
    public Response send(Request request) {
        try (Socket socket = new Socket(host, port);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {

            // 序列化请求
            byte[] requestBytes = serialization.serialize(request);

            // 发送（长度前缀 + 数据体）
            dos.writeInt(requestBytes.length);
            dos.write(requestBytes);
            dos.flush();

            // 接收响应
            int responseLength = dis.readInt();
            byte[] responseBytes = new byte[responseLength];
            dis.readFully(responseBytes);

            return serialization.deserialize(responseBytes, Response.class);

        } catch (Exception e) {
            System.err.println("[ObjectClient] 请求失败: " + e.getMessage());
            return Response.error(-1, e.getMessage(), e.getClass().getName());
        }
    }
}
