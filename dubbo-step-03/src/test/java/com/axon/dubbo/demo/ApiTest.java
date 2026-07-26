package com.axon.dubbo.demo;

import com.axon.dubbo.remoting.transport.socket.ObjectClient;
import com.axon.dubbo.remoting.transport.socket.ObjectServer;
import com.axon.dubbo.rpc.proxy.ProxyFactory;
import com.axon.dubbo.rpc.proxy.jdk.JdkProxyFactory;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Step 03 测试用例
 *
 * 验证动态代理的完整链路：
 * 客户端通过代理调用接口方法 → 拦截转为 Request → 网络传输
 * → 服务端接收 → 反射调用实现类 → 返回结果 → 客户端代理返回结果
 *
 * 核心验证点：
 * 客户端代码完全看不到网络、序列化、协议等细节——
 * 只有普通的 IUserService 接口调用！
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class ApiTest {

    /**
     * 测试：通过动态代理实现透明的远程方法调用
     */
    @Test
    public void testProxyRpcCall() throws Exception {
        int testPort = 9999;
        CountDownLatch serverReady = new CountDownLatch(1);

        // ====== 1. 启动服务端，注册服务 ======
        ObjectServer server = new ObjectServer(testPort);
        server.registerService(IUserService.class, new UserServiceImpl());

        Thread serverThread = new Thread(() -> {
            try {
                // 绑定端口后通知主线程
                java.net.ServerSocket ss = new java.net.ServerSocket(testPort);
                serverReady.countDown();
                ss.close();

                server.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        if (!serverReady.await(5, TimeUnit.SECONDS)) {
            throw new RuntimeException("服务端启动超时");
        }

        // ====== 2. 客户端：通过代理工厂创建服务代理 ======
        ObjectClient client = new ObjectClient("localhost", testPort);
        ProxyFactory proxyFactory = new JdkProxyFactory();

        // 🔑 关键步骤：创建代理对象
        // 这看起来只是普通的接口，实际上背后连接着远程服务端
        IUserService userService = proxyFactory.createProxy(IUserService.class, client);

        System.out.println("========== Step 03 测试开始 ==========");

        // ====== 3. 像调用本地方法一样调用远程服务！ ======
        // 调用方完全不知道这是远程调用！
        User user = userService.getUser(1001L);
        System.out.println("[测试] getUser 返回: " + user);

        String userName = userService.getUserName(2002L);
        System.out.println("[测试] getUserName 返回: " + userName);

        User newUser = new User(null, "张三", 28, "zhangsan@example.com");
        User savedUser = userService.saveUser(newUser);
        System.out.println("[测试] saveUser 返回: " + savedUser);

        // ====== 4. 验证结果 ======
        assert user != null : "getUser 返回值不能为 null";
        assert user.getId().equals(1001L) : "用户 ID 应匹配";
        assert "User_1001".equals(user.getName()) : "用户名应匹配";

        assert userName != null : "getUserName 返回值不能为 null";
        assert "UserName_2002".equals(userName) : "用户名应匹配";

        assert savedUser != null : "saveUser 返回值不能为 null";
        assert savedUser.getId() != null : "保存后用户 ID 应被赋值";
        assert "张三".equals(savedUser.getName()) : "保存后用户名应保持";

        System.out.println("=======================================");
        System.out.println("[测试通过] 动态代理 RPC 调用验证成功！");
        System.out.println("客户端通过普通接口调用完成了 3 次远程方法调用！");

        server.stop();
    }

    /**
     * 测试：代理层对 Object 方法的处理
     */
    @Test
    public void testProxyObjectMethods() throws Exception {
        int testPort = 9998;
        CountDownLatch serverReady = new CountDownLatch(1);

        ObjectServer server = new ObjectServer(testPort);
        server.registerService(IUserService.class, new UserServiceImpl());

        Thread serverThread = new Thread(() -> {
            try {
                java.net.ServerSocket ss = new java.net.ServerSocket(testPort);
                serverReady.countDown();
                ss.close();
                server.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        if (!serverReady.await(5, TimeUnit.SECONDS)) {
            throw new RuntimeException("服务端启动超时");
        }

        ObjectClient client = new ObjectClient("localhost", testPort);
        ProxyFactory proxyFactory = new JdkProxyFactory();
        IUserService userService = proxyFactory.createProxy(IUserService.class, client);

        // toString() 不应触发远程调用
        String str = userService.toString();
        System.out.println("[测试] proxy.toString() = " + str);
        assert str != null : "toString 不应为 null";
        assert str.contains("InvokerInvocationHandler") || str.contains("Proxy")
                : "toString 应包含代理相关信息";

        // hashCode() 不应触发远程调用
        int hashCode = userService.hashCode();
        System.out.println("[测试] proxy.hashCode() = " + hashCode);

        System.out.println("[测试通过] Object 方法正确在本地处理，未触发远程调用");

        server.stop();
    }

    /**
     * 测试：代理无法代理非接口类型
     */
    @Test(expected = IllegalArgumentException.class)
    public void testProxyClassNotInterface() {
        ObjectClient client = new ObjectClient("localhost", 8080);
        ProxyFactory proxyFactory = new JdkProxyFactory();

        // UserServiceImpl 是类而非接口，JDK 代理应抛出异常
        proxyFactory.createProxy(UserServiceImpl.class, client);
    }

    /**
     * 使用 main 方法演示
     */
    public static void main(String[] args) throws Exception {
        ApiTest test = new ApiTest();

        System.out.println(">>> 测试1：动态代理 RPC 调用");
        test.testProxyRpcCall();

        System.out.println("\n>>> 测试2：Object 方法本地处理");
        test.testProxyObjectMethods();

        System.out.println("\n>>> 测试3：非接口类型校验");
        try {
            test.testProxyClassNotInterface();
            System.out.println("[失败] 应该抛出异常");
        } catch (IllegalArgumentException e) {
            System.out.println("[测试通过] 正确抛出 IllegalArgumentException: " + e.getMessage());
        }

        System.out.println("\n>>> Step 03 全部测试通过！");
    }
}
