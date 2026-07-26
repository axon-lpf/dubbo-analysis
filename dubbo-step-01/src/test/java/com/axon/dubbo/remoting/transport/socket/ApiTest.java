package com.axon.dubbo.remoting.transport.socket;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Step 01 测试用例
 *
 * 验证简单的 Socket 通信：服务端接收请求并返回响应。
 *
 * 测试流程：
 * 1. 在独立线程中启动 SimpleServer（监听 9999 端口）
 * 2. 等待服务端就绪
 * 3. 客户端发送请求
 * 4. 验证响应结果
 * 5. 关闭服务端
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class ApiTest {

    /**
     * 单次请求-响应 测试
     */
    @Test
    public void testSimpleRequestResponse() throws Exception {
        int testPort = 9999;
        CountDownLatch serverReady = new CountDownLatch(1);
        CountDownLatch serverStopped = new CountDownLatch(1);

        // 1. 在独立线程中启动服务端
        SimpleServer server = new SimpleServer(testPort);
        Thread serverThread = new Thread(() -> {
            try {
                // 用一个新的 ServerSocket 先绑定端口，确保端口已就绪
                java.net.ServerSocket ss = new java.net.ServerSocket(testPort);
                serverReady.countDown(); // 端口绑定成功，通知主线程

                // accept 一个连接就退出（测试只需要一次）
                java.net.Socket client = ss.accept();
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(client.getInputStream()));
                java.io.PrintWriter writer = new java.io.PrintWriter(client.getOutputStream(), true);

                String request = reader.readLine();
                writer.println("Server Echo: [" + request + "]");

                reader.close();
                writer.close();
                client.close();
                ss.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            serverStopped.countDown();
        });
        serverThread.setDaemon(true);
        serverThread.start();

        // 2. 等待服务端就绪
        boolean ready = serverReady.await(5, TimeUnit.SECONDS);
        if (!ready) {
            throw new RuntimeException("服务端启动超时");
        }

        // 3. 客户端发送请求
        SimpleClient client = new SimpleClient("localhost", testPort);
        Request request = new Request(1, "Hello Step01");
        Response response = client.send(request);

        // 4. 验证响应
        System.out.println("========== Step 01 测试结果 ==========");
        System.out.println("请求: " + request);
        System.out.println("响应: " + response);
        System.out.println("======================================");

        assert response != null : "响应不能为 null";
        assert response.getId() == request.getId() : "响应 ID 应该与请求 ID 一致";
        assert response.getData() != null : "响应数据不能为 null";
        assert response.getData().contains("Server Echo") : "响应应包含服务端回显标识";
        assert response.getData().contains("Hello Step01") : "响应应包含原始请求内容";

        System.out.println("[测试通过] 简单 Socket 通信验证成功！");

        // 5. 等待服务端线程结束
        serverStopped.await(3, TimeUnit.SECONDS);
    }

    /**
     * 使用 main 方法演示（方便开发调试）
     */
    public static void main(String[] args) throws Exception {
        new ApiTest().testSimpleRequestResponse();
    }
}
