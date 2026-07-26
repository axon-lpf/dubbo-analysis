package com.axon.dubbo.remoting.transport.socket;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * 简单的 Socket 服务端
 *
 * 使用 Java 原生 ServerSocket 实现 BIO（阻塞 I/O）通信。
 *
 * 工作流程：
 * 1. 在指定端口上创建 ServerSocket，开始监听
 * 2. 调用 accept() 阻塞等待客户端连接
 * 3. 客户端连接后，通过 BufferedReader 读取请求数据
 * 4. 处理请求（Step 01 简单回显 + 时间戳）
 * 5. 通过 PrintWriter 将响应写回客户端
 * 6. 关闭连接，继续等待下一个客户端连接
 *
 * 这就是 RPC 框架最底层的通信基础。
 * 真实 Dubbo 中，这一步由 Netty 实现（NIO 多路复用，性能远高于 BIO），
 * 但通信的本质是一样的：建立连接 → 发送数据 → 接收响应 → 关闭连接。
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class SimpleServer {

    /**
     * 服务端监听端口
     */
    private final int port;

    /**
     * 标记服务是否在运行
     */
    private volatile boolean running = true;

    public SimpleServer(int port) {
        this.port = port;
    }

    /**
     * 启动服务端，开始监听客户端请求
     */
    public void start() {
        System.out.println("[SimpleServer] 服务端启动中，监听端口: " + port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("[SimpleServer] 服务端启动成功，等待客户端连接...");

            while (running) {
                // accept() 阻塞等待客户端连接
                Socket clientSocket = serverSocket.accept();
                System.out.println("[SimpleServer] 收到客户端连接: " + clientSocket.getInetAddress());

                // 处理本次连接（BIO 模式，一次只处理一个连接）
                handleClient(clientSocket);
            }
        } catch (Exception e) {
            System.err.println("[SimpleServer] 服务端异常: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("[SimpleServer] 服务端已停止");
    }

    /**
     * 处理单个客户端连接
     *
     * 读取客户端发送的请求 → 处理 → 返回响应
     */
    private void handleClient(Socket clientSocket) {
        try (
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            // 1. 读取客户端发送的请求数据
            String requestData = reader.readLine();
            System.out.println("[SimpleServer] 收到请求: " + requestData);

            // 2. 处理请求（这里简单地在消息前加上 "Server Echo" 和时间戳）
            String responseData = processRequest(requestData);

            // 3. 返回响应
            writer.println(responseData);
            System.out.println("[SimpleServer] 返回响应: " + responseData);

        } catch (Exception e) {
            System.err.println("[SimpleServer] 处理客户端请求异常: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 处理请求数据的核心逻辑
     *
     * Step 01 演示：简单地将收到的消息回显，并附上服务端时间戳。
     * 后续步骤会在这里实现：解析 RPC 调用信息 → 反射调用本地服务 → 返回结果。
     */
    private String processRequest(String requestData) {
        return "Server Echo: [" + requestData + "] | Server Time: " + System.currentTimeMillis();
    }

    /**
     * 停止服务
     */
    public void stop() {
        this.running = false;
    }

    /**
     * 启动 Demo
     */
    public static void main(String[] args) {
        SimpleServer server = new SimpleServer(8080);
        server.start();
    }
}
