package com.axon.dubbo.remoting.transport.socket;

import com.axon.dubbo.common.serialize.Serialization;
import com.axon.dubbo.common.serialize.java.JavaSerialization;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

/**
 * 对象序列化客户端
 *
 * 在 Step 01 SimpleClient 的基础上，将字符串通信升级为 Java 对象通信。
 *
 * 完整的一次 RPC 调用经过以下步骤：
 *
 * 客户端                                 服务端
 * ───────                               ───────
 * Request 对象                            收到字节数组
 *    │ serialization.serialize()             │ serialization.deserialize()
 *    ▼                                       ▼
 * 字节数组                                Request 对象
 *    │ Socket.send()                         │ processRequest()
 *    ▼                                       ▼
 * 网络传输 ──────────────────────────→    Response 对象
 *                                         │ serialization.serialize()
 *                                         ▼
 * 收到字节数组  ←─────────────────────── 网络传输
 *    │ serialization.deserialize()
 *    ▼
 * Response 对象
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
     * 发送请求并获取响应（对象序列化版本）
     *
     * @param request RPC 请求对象
     * @return RPC 响应对象
     */
    public Response send(Request request) {
        System.out.println("[ObjectClient] 准备发送请求: " + request);

        try (Socket socket = new Socket(host, port);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {

            // ====== 1. 序列化请求（对象 → 字节） ======
            byte[] requestBytes = serialization.serialize(request);
            System.out.println("[ObjectClient] 序列化请求完成，长度: " + requestBytes.length + " bytes");

            // ====== 2. 发送请求（长度前缀 + 数据体） ======
            // 先写长度前缀（4 字节 int），接收方才能知道后面要读多少字节
            dos.writeInt(requestBytes.length);
            dos.write(requestBytes);
            dos.flush();
            System.out.println("[ObjectClient] 请求已发送");

            // ====== 3. 接收响应（长度前缀 + 数据体） ======
            // 先读长度前缀
            int responseLength = dis.readInt();
            System.out.println("[ObjectClient] 收到响应，长度: " + responseLength + " bytes");

            // 再读数据体
            byte[] responseBytes = new byte[responseLength];
            dis.readFully(responseBytes);

            // ====== 4. 反序列化响应（字节 → 对象） ======
            Response response = serialization.deserialize(responseBytes, Response.class);
            System.out.println("[ObjectClient] 反序列化响应: " + response);

            return response;

        } catch (Exception e) {
            System.err.println("[ObjectClient] 请求失败: " + e.getMessage());
            e.printStackTrace();
            return Response.error(-1, e.getMessage(), e.getClass().getName());
        }
    }

    public static void main(String[] args) {
        ObjectClient client = new ObjectClient("localhost", 8080);

        // 构造 RPC 请求
        Request request = new Request(
                1L,
                "com.axon.dubbo.demo.UserService",  // 接口名
                "getUser",                           // 方法名
                new String[]{"java.lang.Long"},       // 参数类型
                new Object[]{1001L}                   // 参数值
        );

        Response response = client.send(request);

        System.out.println("=============================");
        System.out.println("最终结果: " + response);
        System.out.println("=============================");
    }
}
