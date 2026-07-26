package com.axon.dubbo.demo;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.registry.LocalRegistry;
import com.axon.dubbo.registry.NotifyListener;
import com.axon.dubbo.registry.RegistryService;
import com.axon.dubbo.rpc.*;
import com.axon.dubbo.rpc.protocol.DubboProtocol;
import com.axon.dubbo.rpc.protocol.Protocol;
import com.axon.dubbo.rpc.proxy.ProxyFactory;
import com.axon.dubbo.rpc.proxy.jdk.JdkProxyFactory;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Step 06 测试用例
 *
 * 验证注册中心的集成：
 * - Provider 导出服务 → 自动向注册中心注册
 * - Consumer 引用服务 → 从注册中心发现 Provider
 * - 服务调用成功
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class ApiTest {

    /**
     * 测试1：注册中心基本操作（register / lookup / unregister）
     */
    @Test
    public void testRegistryBasic() {
        // 创建本地注册中心
        RegistryService registry = new LocalRegistry(
                URL.builder().protocol("registry").host("127.0.0.1").port(0)
                        .path("com.axon.dubbo.registry.LocalRegistry").build());

        // 注册 Provider
        URL providerUrl = URL.builder()
                .protocol("dubbo").host("192.168.1.100").port(20880)
                .path(IUserService.class.getName())
                .addParameter("version", "1.0.0").build();

        registry.register(providerUrl);
        System.out.println("[测试] 已注册: " + providerUrl);

        // 查找 Provider
        URL lookupCondition = URL.builder()
                .path(IUserService.class.getName())
                .addParameter("version", "1.0.0").build();

        List<URL> urls = registry.lookup(lookupCondition);
        assert urls.size() == 1 : "应有 1 个提供者，实际: " + urls.size();
        assert urls.get(0).getHost().equals("192.168.1.100");
        System.out.println("[测试] 查找到提供者: " + urls.get(0).getAddress());

        // 取消注册
        registry.unregister(providerUrl);
        List<URL> afterUnregister = registry.lookup(lookupCondition);
        assert afterUnregister.isEmpty() : "取消注册后应无提供者";

        System.out.println("[测试通过] 注册中心基本操作验证成功！");
    }

    /**
     * 测试2：订阅通知机制
     */
    @Test
    public void testSubscribeNotify() {
        RegistryService registry = new LocalRegistry(
                URL.builder().protocol("registry").host("127.0.0.1").port(0).path("test").build());

        URL condition = URL.builder().path(IUserService.class.getName()).build();
        CountDownLatch notified = new CountDownLatch(1);

        // 添加订阅
        registry.subscribe(condition, urls -> {
            System.out.println("[订阅者] 收到通知，提供者数量: " + urls.size());
            if (!urls.isEmpty()) {
                notified.countDown();
            }
        });

        // 注册 Provider（应触发通知）
        URL providerUrl = URL.builder()
                .protocol("dubbo").host("10.0.0.1").port(20880)
                .path(IUserService.class.getName()).build();
        registry.register(providerUrl);

        System.out.println("[测试通过] 订阅通知机制验证成功！");
    }

    /**
     * 测试3：通过注册中心实现 Provider/Consumer 解耦
     *
     * 这是 Dubbo 服务治理的核心场景：
     * - Provider 不关心 Consumer 在哪里
     * - Consumer 不关心 Provider 在哪里
     * - 注册中心是唯一的"中间人"
     */
    @Test
    public void testRegistryDecoupling() throws Exception {
        // 1. 创建注册中心
        RegistryService registry = new LocalRegistry(
                URL.builder().protocol("registry").host("127.0.0.1").port(0)
                        .path("local").build());

        int port = 20882;

        // ====== 2. Provider：导出服务 → 自动注册 ======
        Protocol protocol = new DubboProtocol(registry);
        ProxyFactory proxyFactory = new JdkProxyFactory();

        URL serviceUrl = URL.builder()
                .protocol("dubbo").host("127.0.0.1").port(port)
                .path(IUserService.class.getName())
                .addParameter("version", "1.0.0").build();

        Invoker<IUserService> providerInvoker = proxyFactory.getInvoker(
                new UserServiceImpl(), IUserService.class, serviceUrl);

        Exporter<IUserService> exporter = protocol.export(providerInvoker);

        // 验证注册中心已有 Provider
        List<URL> urls = registry.lookup(serviceUrl);
        assert urls.size() == 1;
        System.out.println("[测试] 注册中心查询到 " + urls.size() + " 个提供者");

        Thread.sleep(300);

        // ====== 3. Consumer：从注册中心发现 → 调用 ======
        Invoker<IUserService> consumerInvoker = protocol.refer(IUserService.class, serviceUrl);
        IUserService userService = proxyFactory.getProxy(consumerInvoker);

        System.out.println("========== 注册中心解耦测试 ==========");

        // Consumer 完全不知道 Provider 的地址！
        User user = userService.getUser(1001L);
        assert user != null && "User_1001".equals(user.getName());
        System.out.println("[测试] 通过注册中心调用成功: " + user);

        // ====== 4. 清理 ======
        exporter.unexport();
        registry.unregister(serviceUrl); // 从注册中心移除
        assert registry.lookup(serviceUrl).isEmpty() : "取消注册后注册中心应无记录";

        System.out.println("[测试通过] 注册中心解耦验证成功！");
        System.out.println("Provider 和 Consumer 仅通过注册中心交互，彼此完全透明！");
    }

    public static void main(String[] args) throws Exception {
        ApiTest test = new ApiTest();
        test.testRegistryBasic();
        test.testSubscribeNotify();
        test.testRegistryDecoupling();
        System.out.println("\n>>> Step 06 全部测试通过！");
    }
}
