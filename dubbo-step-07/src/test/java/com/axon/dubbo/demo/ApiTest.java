package com.axon.dubbo.demo;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.registry.LocalRegistry;
import com.axon.dubbo.registry.RegistryService;
import com.axon.dubbo.rpc.*;
import com.axon.dubbo.rpc.cluster.directory.RegistryDirectory;
import com.axon.dubbo.rpc.protocol.DubboProtocol;
import com.axon.dubbo.rpc.protocol.Protocol;
import com.axon.dubbo.rpc.proxy.ProxyFactory;
import com.axon.dubbo.rpc.proxy.jdk.JdkProxyFactory;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Step 07 测试用例
 *
 * 验证 Directory 动态服务发现：
 * - Directory 订阅注册中心，自动感知 Provider 上下线
 * - Provider 上线 → Directory 收到通知 → list 包含新 Invoker
 * - Provider 下线 → Directory 收到通知 → list 移除旧 Invoker
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class ApiTest {

    /**
     * 测试1：RegistryDirectory 订阅并感知 Provider 变化
     */
    @Test
    public void testDirectoryProviderChange() throws Exception {
        RegistryService registry = new LocalRegistry(
                URL.builder().protocol("registry").host("127.0.0.1").port(0).path("local").build());

        // ====== 1. 创建 Directory（订阅注册中心，此时 Provider 为 0） ======
        URL consumerUrl = URL.builder()
                .path(IUserService.class.getName())
                .addParameter("version", "1.0.0").build();

        CountDownLatch firstNotify = new CountDownLatch(1);
        RegistryDirectory<IUserService> directory = new RegistryDirectory<>(
                IUserService.class, consumerUrl, registry,
                providerUrl -> new com.axon.dubbo.rpc.protocol.DubboInvoker<>(
                        IUserService.class, providerUrl,
                        new com.axon.dubbo.remoting.exchange.DubboCodec())
        );

        // 初始状态：无 Provider
        List<Invoker<IUserService>> initial = directory.list(null);
        System.out.println("[测试] 初始 Invoker 数量: " + initial.size());
        assert initial.isEmpty() : "注册时无 Provider，列表应为空";

        // ====== 2. 注册一个 Provider → Directory 应自动收到通知 ======
        URL provider1 = URL.builder()
                .protocol("dubbo").host("10.0.0.1").port(20880)
                .path(IUserService.class.getName())
                .addParameter("version", "1.0.0").build();

        registry.register(provider1);
        Thread.sleep(100); // 等待通知异步到达

        List<Invoker<IUserService>> list1 = directory.list(null);
        System.out.println("[测试] Provider 1 注册后 Invoker 数量: " + list1.size());
        assert list1.size() == 1 : "注册 1 个 Provider 后应有 1 个 Invoker";

        // ====== 3. 注册第二个 Provider → 列表应更新 ======
        URL provider2 = URL.builder()
                .protocol("dubbo").host("10.0.0.2").port(20880)
                .path(IUserService.class.getName())
                .addParameter("version", "1.0.0").build();

        registry.register(provider2);
        Thread.sleep(100);

        List<Invoker<IUserService>> list2 = directory.list(null);
        System.out.println("[测试] Provider 2 注册后 Invoker 数量: " + list2.size());
        assert list2.size() == 2 : "注册 2 个 Provider 后应有 2 个 Invoker";

        // ====== 4. 下线 Provider 1 → 列表应收缩 ======
        registry.unregister(provider1);
        Thread.sleep(100);

        List<Invoker<IUserService>> list3 = directory.list(null);
        System.out.println("[测试] Provider 1 下线后 Invoker 数量: " + list3.size());
        assert list3.size() == 1 : "Provider 1 下线后应只剩 1 个 Invoker";
        assert list3.get(0).getUrl().getHost().equals("10.0.0.2");

        System.out.println("[测试通过] Directory 动态感知 Provider 变化验证成功！");

        directory.destroy();
    }

    /**
     * 测试2：通过 Directory 完成端到端 RPC 调用
     */
    @Test
    public void testDirectoryRpcCall() throws Exception {
        RegistryService registry = new LocalRegistry(
                URL.builder().protocol("registry").host("127.0.0.1").port(0).path("local").build());

        int port = 20883;

        // ====== Provider ======
        Protocol protocol = new DubboProtocol(registry);
        ProxyFactory proxyFactory = new JdkProxyFactory();

        URL serviceUrl = URL.builder()
                .protocol("dubbo").host("127.0.0.1").port(port)
                .path(IUserService.class.getName())
                .addParameter("version", "1.0.0").build();

        Invoker<IUserService> providerInvoker = proxyFactory.getInvoker(
                new UserServiceImpl(), IUserService.class, serviceUrl);
        Exporter<IUserService> exporter = protocol.export(providerInvoker);

        Thread.sleep(300);

        // ====== Consumer：通过 Directory 发现 ======
        Invoker<IUserService> consumerInvoker = protocol.refer(IUserService.class, serviceUrl);
        IUserService userService = proxyFactory.getProxy(consumerInvoker);

        System.out.println("========== Directory RPC 调用测试 ==========");
        User user = userService.getUser(1001L);
        assert user != null && "User_1001".equals(user.getName());
        System.out.println("[测试] 通过 Directory 调用成功: " + user);

        System.out.println("[测试通过] Directory + RPC 端到端验证成功！");

        exporter.unexport();
        registry.unregister(serviceUrl);
    }

    /**
     * 测试3：Directory 在无 Provider 时返回空列表
     */
    @Test
    public void testDirectoryNoProvider() throws Exception {
        RegistryService registry = new LocalRegistry(
                URL.builder().protocol("registry").host("127.0.0.1").port(0).path("local").build());

        URL consumerUrl = URL.builder()
                .path("com.axon.dubbo.demo.UnknownService")
                .addParameter("version", "1.0.0").build();

        RegistryDirectory<com.axon.dubbo.demo.IUserService> directory = new RegistryDirectory<>(
                IUserService.class, consumerUrl, registry,
                providerUrl -> {
                    throw new RuntimeException("不应该被调用——没有 Provider");
                }
        );

        List<Invoker<IUserService>> list = directory.list(null);
        assert list.isEmpty() : "无 Provider 时列表应为空";

        System.out.println("[测试通过] 无 Provider 时 Directory 返回空列表");
        directory.destroy();
    }

    public static void main(String[] args) throws Exception {
        ApiTest test = new ApiTest();
        test.testDirectoryNoProvider();
        test.testDirectoryProviderChange();
        test.testDirectoryRpcCall();
        System.out.println("\n>>> Step 07 全部测试通过！");
    }
}
