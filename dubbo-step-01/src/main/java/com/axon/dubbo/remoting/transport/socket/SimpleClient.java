package com.axon.dubbo.remoting.transport.socket;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * 简单的 Socket 客户端
 *
 * 使用 Java 原生 Socket 与服务端通信。
 *
 * 工作流程：
 * 1. 通过 Socket 连接到指定主机和端口
 * 2. 通过 PrintWriter 发送请求数据
 * 3. 通过 BufferedReader 读取服务端响应
 * 4. 关闭连接
 *
 * 这就是 RPC 调用在网络上最原始的形态：
 * "发送请求 → 等待响应 → 拿到结果"
 *
 * 后续步骤中，我们会通过"动态代理"把这些网络细节隐藏起来，
 * 让远程调用看起来就像本地方法调用一样。
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class SimpleClient {

    /**
     * 服务端主机地址
     */
    private final String host;

    /**
     * 服务端端口
     */
    private final int port;

    public SimpleClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * 发送请求并获取响应
     *
     * 一次完整的同步 RPC 调用过程。
     *
     * @param request 请求对象
     * @return 响应对象
     */
    public Response send(Request request) {
        System.out.println("[SimpleClient] 发送请求: " + request);

        try (Socket socket = new Socket(host, port);
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(socket.getInputStream()))) {

            // 1. 发送请求数据（将 Request 转为字符串发送）
            writer.println(request.getData());

            // 2. 读取响应数据
            String responseData = reader.readLine();
            System.out.println("[SimpleClient] 收到响应: " + responseData);

            // 3. 封装为 Response 对象返回
            return new Response(request.getId(), responseData);

        } catch (Exception e) {
            System.err.println("[SimpleClient] 请求失败: " + e.getMessage());
            e.printStackTrace();

            // 返回错误响应
            return new Response(request.getId(), "Error: " + e.getMessage());
        }
    }

    /**
     * 启动 Demo —— 在 main 中测试完整调用
     */
    public static void main(String[] args) {
        SimpleClient client = new SimpleClient("localhost", 8080);

        Request request = new Request(1, "Hello Dubbo!");
        Response response = client.send(request);

        System.out.println("=============================");
        System.out.println("最终结果: " + response);
        System.out.println("=============================");
    }
}
