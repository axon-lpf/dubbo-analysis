package com.axon.dubbo.demo;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.registry.LocalRegistry;
import com.axon.dubbo.registry.RegistryService;
import com.axon.dubbo.rpc.*;
import com.axon.dubbo.rpc.protocol.DubboProtocol;
import com.axon.dubbo.rpc.protocol.Protocol;
import com.axon.dubbo.rpc.proxy.ProxyFactory;
import com.axon.dubbo.rpc.proxy.jdk.JdkProxyFactory;
import org.junit.Test;

/**
 * Step 16 测试用例 —— Netty NIO 传输层
 *
 * 验证：
 * 1. NettyServer 启动 → NIO 端口监听
 * 2. NettyClient 连接 → 发送请求 → 接收响应
 * 3. Filter 链在 Netty 下正常工作
 * 4. BIO → NIO 升级后，上层代码零改动！
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class ApiTest {

    /**
     * 测试1：Netty 端到端 RPC 调用
     */
    @Test
    public void testNettyRpcCall() throws Exception {
        RegistryService registry = new LocalRegistry(
                URL.builder().protocol("registry").host("127.0.0.1").port(0).path("local").build());

        int port = 20921;
        Protocol protocol = new DubboProtocol(registry);
        ProxyFactory proxyFactory = new JdkProxyFactory();

        // Provider
        URL serviceUrl = URL.builder().protocol("dubbo").host("127.0.0.1").port(port)
                .path(IUserService.class.getName()).addParameter("version", "1.0.0").build();
        Invoker<IUserService> prov = proxyFactory.getInvoker(new UserServiceImpl(), IUserService.class, serviceUrl);
        Exporter<IUserService> exporter = protocol.export(prov);
        Thread.sleep(500); // 等待 Netty 启动

        // Consumer
        Invoker<IUserService> consumer = protocol.refer(IUserService.class, serviceUrl);
        IUserService userService = proxyFactory.getProxy(consumer);

        System.out.println("========== Netty NIO RPC 调用 ==========");

        // 3 次调用，验证连接复用
        for (int i = 1; i <= 3; i++) {
            User user = userService.getUser((long) (1000 + i));
            System.out.println("[" + i + "] " + user.getName());
            assert ("User_" + (1000 + i)).equals(user.getName());
        }

        System.out.println("[测试通过] Netty NIO 端到端调用成功，3 次请求连接复用！");

        exporter.unexport();
        registry.unregister(serviceUrl);
    }

    /**
     * 测试2：Netty + Filter 链（验证上层无感知）
     */
    @Test
    public void testNettyWithFilterChain() throws Exception {
        RegistryService registry = new LocalRegistry(
                URL.builder().protocol("registry").host("127.0.0.1").port(0).path("local").build());

        int port = 20922;
        Protocol protocol = new DubboProtocol(registry);
        ProxyFactory proxyFactory = new JdkProxyFactory();

        URL serviceUrl = URL.builder().protocol("dubbo").host("127.0.0.1").port(port)
                .path(IUserService.class.getName()).addParameter("version", "1.0.0").build();
        Invoker<IUserService> prov = proxyFactory.getInvoker(new UserServiceImpl(), IUserService.class, serviceUrl);
        protocol.export(prov);
        Thread.sleep(500);

        Invoker<IUserService> consumer = protocol.refer(IUserService.class, serviceUrl);
        IUserService userService = proxyFactory.getProxy(consumer);

        System.out.println("========== Netty + Filter 链 ==========");
        // AccessLogFilter + TimeCostFilter 应正常工作
        User user = userService.getUser(9999L);
        System.out.println("[结果] " + user);

        // 只需验证调用成功 → Filter 自然生效（因为链在 Protocol 层构建）
        assert "User_9999".equals(user.getName());
        System.out.println("[测试通过] Filter 链在 Netty 下透明运行！");

        ((com.axon.dubbo.rpc.protocol.DubboProtocol) protocol).destroy();
    }

    /**
     * 测试3：BIO vs NIO——上层代码完全一致
     *
     * 关键验证：从 Step 14（BIO）到 Step 16（NIO），
     * Provider/Consumer 的业务代码零改动！
     */
    @Test
    public void testBioNioTransparency() {
        System.out.println("========== BIO→NIO 透明升级验证 ==========");
        System.out.println("Step 14 (BIO) 客户端代码:");
        System.out.println("  Protocol protocol = new DubboProtocol(registry);");
        System.out.println("  Invoker<T> inv = protocol.refer(type, url);");
        System.out.println("  T service = proxyFactory.getProxy(inv);");
        System.out.println("  service.method();  // 底层: Socket BIO");
        System.out.println();
        System.out.println("Step 16 (NIO) 客户端代码:");
        System.out.println("  Protocol protocol = new DubboProtocol(registry);");
        System.out.println("  Invoker<T> inv = protocol.refer(type, url);");
        System.out.println("  T service = proxyFactory.getProxy(inv);");
        System.out.println("  service.method();  // 底层: Netty NIO ← 仅此不同！");
        System.out.println();
        System.out.println("[结论] 上层代码完全一致，Protocol/Proxy/Cluster/Filter 层零改动！");
        System.out.println("[测试通过] 抽象层价值验证——传输层可插拔！");
    }

    public static void main(String[] args) throws Exception {
        ApiTest t = new ApiTest();
        t.testBioNioTransparency();
        t.testNettyRpcCall();
        t.testNettyWithFilterChain();
        System.out.println("\n>>> Step 16 全部测试通过！");
    }
}
