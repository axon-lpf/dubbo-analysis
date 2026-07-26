package com.axon.dubbo.remoting.transport.socket;

import com.axon.dubbo.common.serialize.Serialization;
import com.axon.dubbo.common.serialize.java.JavaSerialization;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * 对象序列化服务端
 *
 * 在 Step 01 SimpleServer 的基础上，将字符串通信升级为 Java 对象通信。
 *
 * 核心变化：
 * 1. 不再发送字符串，而是发送序列化后的字节数组
 * 2. 使用 DataInputStream/DataOutputStream 处理长度前缀（解决粘包问题）
 * 3. 通过 Serialization 接口实现对象与字节的互转
 *
 * 通信协议（简易版）：
 * ┌──────────────┬──────────────────────┐
 * │ 4 bytes      │ N bytes              │
 * │ 数据长度(int) │ 序列化后的对象字节数组  │
 * └──────────────┴──────────────────────┘
 *
 * 这个"长度前缀"协议是 Dubbo 协议头的简化版，
 * 正因为有了长度前缀，接收方才知道要读取多少字节。
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class ObjectServer {

    private final int port;
    private final Serialization serialization;
    private volatile boolean running = true;

    public ObjectServer(int port) {
        this.port = port;
        // 当前使用 JDK 原生序列化，后续通过 SPI 机制动态加载
        this.serialization = new JavaSerialization();
    }

    public ObjectServer(int port, Serialization serialization) {
        this.port = port;
        this.serialization = serialization;
    }

    /**
     * 启动服务端
     */
    public void start() {
        System.out.println("[ObjectServer] 服务端启动中，监听端口: " + port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("[ObjectServer] 服务端启动成功，等待客户端连接...");

            while (running) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[ObjectServer] 收到客户端连接: " + clientSocket.getInetAddress());
                handleClient(clientSocket);
            }
        } catch (Exception e) {
            System.err.println("[ObjectServer] 服务端异常: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("[ObjectServer] 服务端已停止");
    }

    /**
     * 处理单个客户端连接（对象序列化版本）
     */
    private void handleClient(Socket clientSocket) {
        try (DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
             DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream())) {

            // ====== 1. 读取请求（字节 → 对象） ======
            // 先读长度前缀
            int requestLength = dis.readInt();
            System.out.println("[ObjectServer] 收到请求数据，长度: " + requestLength + " bytes");

            // 再读数据体
            byte[] requestBytes = new byte[requestLength];
            dis.readFully(requestBytes);

            // 反序列化：字节 → Request 对象
            Request request = serialization.deserialize(requestBytes, Request.class);
            System.out.println("[ObjectServer] 反序列化请求: " + request);

            // ====== 2. 处理请求（业务逻辑） ======
            Response response = processRequest(request);

            // ====== 3. 返回响应（对象 → 字节） ======
            byte[] responseBytes = serialization.serialize(response);
            System.out.println("[ObjectServer] 序列化响应，长度: " + responseBytes.length + " bytes");

            // 先写长度前缀
            dos.writeInt(responseBytes.length);
            // 再写数据体
            dos.write(responseBytes);
            dos.flush();
            System.out.println("[ObjectServer] 返回响应: " + response);

        } catch (Exception e) {
            System.err.println("[ObjectServer] 处理客户端请求异常: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 处理请求的核心逻辑
     *
     * Step 02 演示：将请求回显 + 附加服务端时间戳。
     * 后续步骤会替换为：查找服务 → 反射调用 → 返回结果。
     */
    private Response processRequest(Request request) {
        // 模拟服务端处理
        String result = String.format(
                "服务端处理完成 | 接口=%s | 方法=%s | 参数=%s | 时间=%d",
                request.getInterfaceName(),
                request.getMethodName(),
                java.util.Arrays.toString(request.getArguments()),
                System.currentTimeMillis()
        );
        return Response.success(request.getId(), result);
    }

    public void stop() {
        this.running = false;
    }

    public static void main(String[] args) {
        new ObjectServer(8080).start();
    }
}
