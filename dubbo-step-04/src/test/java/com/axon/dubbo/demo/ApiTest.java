package com.axon.dubbo.demo;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.remoting.transport.socket.ExporterServer;
import com.axon.dubbo.remoting.transport.socket.ObjectClient;
import com.axon.dubbo.rpc.*;
import com.axon.dubbo.rpc.proxy.ProxyFactory;
import com.axon.dubbo.rpc.proxy.jdk.JdkProxyFactory;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Step 04 测试用例
 *
 * 验证 Invoker/Exporter 体系的完整链路：
 * - Provider 端：实现类 → Invoker → Exporter → ExporterServer
 * - Consumer 端：Proxy → InvokerInvocationHandler → ObjectClient → 网络
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class ApiTest {

    @Test
    public void testExporterServer() throws Exception {
        int port = 9999;
        CountDownLatch ready = new CountDownLatch(1);

        // 1. 创建服务端
        ExporterServer server = new ExporterServer(port);

        // 2. 导出服务（实现类 → Invoker → Exporter → 注册）
        server.export(IUserService.class, new UserServiceImpl());

        Thread serverThread = new Thread(() -> {
            try {
                java.net.ServerSocket ss = new java.net.ServerSocket(port);
                ready.countDown(); ss.close();
                server.start();
            } catch (Exception e) { e.printStackTrace(); }
        });
        serverThread.setDaemon(true);
        serverThread.start();
        if (!ready.await(5, TimeUnit.SECONDS)) throw new RuntimeException("服务端启动超时");

        // 3. 客户端：创建代理
        ObjectClient client = new ObjectClient("localhost", port);
        ProxyFactory proxyFactory = new JdkProxyFactory();
        IUserService userService = proxyFactory.createProxy(IUserService.class, client);

        System.out.println("========== Step 04 测试 ==========");

        // 4. 透明远程调用
        User user = userService.getUser(1001L);
        assert user != null && user.getId().equals(1001L);
        System.out.println("[测试] getUser: " + user);

        String name = userService.getUserName(2002L);
        assert "UserName_2002".equals(name);
        System.out.println("[测试] getUserName: " + name);

        User saved = userService.saveUser(new User(null, "李四", 30, "lisi@test.com"));
        assert saved.getId() != null;
        System.out.println("[测试] saveUser: " + saved);

        System.out.println("[测试通过] Invoker/Exporter 体系验证成功！");
        server.stop();
    }

    /**
     * 测试 Invoker 直接调用（不经过网络）
     */
    @Test
    public void testDirectInvokerCall() throws Exception {
        // Provider 端：直接将本地实现包装为 Invoker
        URL url = URL.builder()
                .protocol("dubbo").host("127.0.0.1").port(20880)
                .path(IUserService.class.getName())
                .addParameter("version", "1.0.0").build();

        ProxyFactory proxyFactory = new JdkProxyFactory();
        Invoker<IUserService> invoker = proxyFactory.getInvoker(
                new UserServiceImpl(), IUserService.class, url);

        System.out.println("========== Direct Invoker 调用 ==========");
        System.out.println("Invoker URL: " + invoker.getUrl());
        System.out.println("Invoker Interface: " + invoker.getInterface().getName());
        System.out.println("Invoker Available: " + invoker.isAvailable());

        // 直接调用 Invoker（不经过网络！）
        Invocation invocation = new RpcInvocation(
                IUserService.class.getName(), "getUser",
                new String[]{"java.lang.Long"}, new Object[]{1001L});

        Result result = invoker.invoke(invocation);
        assert !result.hasException() : "不应该有异常";
        User user = (User) result.getValue();
        assert "User_1001".equals(user.getName());

        System.out.println("Direct Invoker result: " + result);
        System.out.println("[测试通过] Invoker 直连调用验证成功！");
    }

    /**
     * 测试 Result.recreate() 方法
     */
    @Test
    public void testResultRecreate() throws Throwable {
        // 成功 Result
        RpcResult success = new RpcResult("Hello");
        assert "Hello".equals(success.recreate());

        // 失败 Result
        RpcResult failure = new RpcResult(new RuntimeException("测试异常"));
        try {
            failure.recreate();
            assert false : "应该抛出异常";
        } catch (Throwable t) {
            assert t.getMessage().equals("测试异常");
        }

        System.out.println("[测试通过] Result.recreate() 验证成功！");
    }

    public static void main(String[] args) throws Throwable {
        ApiTest test = new ApiTest();
        test.testDirectInvokerCall();
        test.testResultRecreate();
        test.testExporterServer();
        System.out.println("\n>>> Step 04 全部测试通过！");
    }
}
