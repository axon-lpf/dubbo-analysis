package com.axon.dubbo.remoting.transport.socket;

import com.axon.dubbo.common.serialize.Serialization;
import com.axon.dubbo.common.serialize.java.JavaSerialization;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Step 02 测试用例
 *
 * 验证对象序列化 + Socket 传输的完整链路：
 * Request 对象 → 序列化 → 发送 → 接收 → 反序列化 → Response 对象
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class ApiTest {

    /**
     * 测试：序列化往返（不经过网络，纯本地）
     *
     * 验证序列化/反序列化本身的正确性，
     * 排除网络因素，方便排查问题。
     */
    @Test
    public void testSerializationRoundTrip() throws Exception {
        Serialization serialization = new JavaSerialization();

        // 构造请求
        Request original = new Request(
                1L,
                "com.axon.dubbo.demo.UserService",
                "getUser",
                new String[]{"java.lang.Long"},
                new Object[]{1001L}
        );

        // 序列化
        byte[] bytes = serialization.serialize(original);
        System.out.println("[RoundTrip] 序列化后字节数: " + bytes.length);

        // 反序列化
        Request restored = serialization.deserialize(bytes, Request.class);
        System.out.println("[RoundTrip] 反序列化结果: " + restored);

        // 验证所有字段
        assert restored.getId() == original.getId() : "ID 不匹配";
        assert original.getInterfaceName().equals(restored.getInterfaceName()) : "接口名不匹配";
        assert original.getMethodName().equals(restored.getMethodName()) : "方法名不匹配";
        assert original.getArguments()[0].equals(restored.getArguments()[0]) : "参数不匹配";

        System.out.println("[RoundTrip 测试通过] 序列化/反序列化数据完整");
    }

    /**
     * 测试：通过 Socket 的完整 RPC 调用
     */
    @Test
    public void testObjectRpcCall() throws Exception {
        int testPort = 9999;
        CountDownLatch serverReady = new CountDownLatch(1);
        CountDownLatch serverClosed = new CountDownLatch(1);

        // 1. 启动服务端
        Thread serverThread = new Thread(() -> {
            try (java.net.ServerSocket ss = new java.net.ServerSocket(testPort)) {
                serverReady.countDown(); // 端口绑定成功

                java.net.Socket client = ss.accept();
                java.io.DataInputStream dis = new java.io.DataInputStream(client.getInputStream());
                java.io.DataOutputStream dos = new java.io.DataOutputStream(client.getOutputStream());

                // 读取请求
                int reqLen = dis.readInt();
                byte[] reqBytes = new byte[reqLen];
                dis.readFully(reqBytes);

                JavaSerialization s = new JavaSerialization();
                Request request = s.deserialize(reqBytes, Request.class);
                System.out.println("[测试服务端] 收到请求: " + request);

                // 处理请求
                String result = "Hello " + request.getArguments()[0] + "! 来自服务端的问候。";
                Response response = Response.success(request.getId(), result);

                // 发送响应
                byte[] respBytes = s.serialize(response);
                dos.writeInt(respBytes.length);
                dos.write(respBytes);
                dos.flush();

                dis.close();
                dos.close();
                client.close();
                ss.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            serverClosed.countDown();
        });
        serverThread.setDaemon(true);
        serverThread.start();

        // 2. 等待服务端就绪
        if (!serverReady.await(5, TimeUnit.SECONDS)) {
            throw new RuntimeException("服务端启动超时");
        }

        // 3. 客户端发起 RPC 调用
        ObjectClient client = new ObjectClient("localhost", testPort);
        Request request = new Request(
                1L,
                "com.axon.dubbo.demo.UserService",
                "getUser",
                new String[]{"java.lang.Long"},
                new Object[]{1001L}
        );

        Response response = client.send(request);

        // 4. 验证结果
        System.out.println("========== Step 02 测试结果 ==========");
        System.out.println("请求: " + request);
        System.out.println("响应: " + response);
        System.out.println("=======================================");

        assert response != null : "响应不能为 null";
        assert response.isSuccess() : "响应应标记为成功";
        assert response.getId() == request.getId() : "响应 ID 应与请求 ID 一致";
        assert response.getResult() != null : "响应结果不能为 null";
        assert response.getResult().toString().contains("来自服务端的问候") : "响应内容应包含服务端标识";

        System.out.println("[测试通过] 对象序列化 RPC 调用验证成功！");

        // 5. 等待服务端关闭
        serverClosed.await(3, TimeUnit.SECONDS);
    }

    /**
     * 测试：模拟服务端处理异常
     */
    @Test
    public void testErrorResponse() throws Exception {
        // 这个测试不需要网络，直接验证 Response 的 error 方法
        Response errorResponse = Response.error(
                1L,
                "模拟的业务异常：用户不存在",
                "java.lang.IllegalArgumentException"
        );

        assert !errorResponse.isSuccess() : "应标记为失败";
        assert errorResponse.getErrorMessage() != null : "错误信息不能为 null";
        assert errorResponse.getErrorMessage().contains("用户不存在") : "错误信息内容不正确";

        // 验证序列化往返
        JavaSerialization s = new JavaSerialization();
        byte[] bytes = s.serialize(errorResponse);
        Response restored = s.deserialize(bytes, Response.class);

        assert !restored.isSuccess() : "反序列化后仍应为失败状态";
        assert restored.getErrorMessage().equals(errorResponse.getErrorMessage()) : "错误信息应完整保留";

        System.out.println("[测试通过] 异常响应序列化验证成功！");
    }

    /**
     * 使用 main 方法演示（方便开发调试）
     */
    public static void main(String[] args) throws Exception {
        ApiTest test = new ApiTest();
        System.out.println(">>> 测试1：序列化往返");
        test.testSerializationRoundTrip();

        System.out.println("\n>>> 测试2：完整 RPC 调用");
        test.testObjectRpcCall();

        System.out.println("\n>>> 测试3：异常响应");
        test.testErrorResponse();

        System.out.println("\n>>> 全部测试通过！");
    }
}
