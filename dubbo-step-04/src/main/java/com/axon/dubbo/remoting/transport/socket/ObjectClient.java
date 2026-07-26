package com.axon.dubbo.remoting.transport.socket;

import com.axon.dubbo.common.serialize.Serialization;
import com.axon.dubbo.common.serialize.java.JavaSerialization;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

public class ObjectClient {
    private final String host;
    private final int port;
    private final Serialization serialization;

    public ObjectClient(String host, int port) {
        this.host = host; this.port = port; this.serialization = new JavaSerialization();
    }

    public Response send(Request request) {
        try (Socket socket = new Socket(host, port);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {
            byte[] reqBytes = serialization.serialize(request);
            dos.writeInt(reqBytes.length); dos.write(reqBytes); dos.flush();
            int respLen = dis.readInt();
            byte[] respBytes = new byte[respLen];
            dis.readFully(respBytes);
            return serialization.deserialize(respBytes, Response.class);
        } catch (Exception e) {
            return Response.error(-1, e.getMessage(), e.getClass().getName());
        }
    }
}
