package com.axon.dubbo.demo;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.registry.LocalRegistry;
import com.axon.dubbo.registry.RegistryService;
import com.axon.dubbo.rpc.*;
import com.axon.dubbo.rpc.cluster.Directory;
import com.axon.dubbo.rpc.cluster.directory.RegistryDirectory;
import com.axon.dubbo.rpc.cluster.directory.StaticDirectory;
import com.axon.dubbo.rpc.protocol.DubboInvoker;
import com.axon.dubbo.rpc.protocol.DubboProtocol;
import com.axon.dubbo.rpc.protocol.Protocol;
import com.axon.dubbo.rpc.proxy.ProxyFactory;
import com.axon.dubbo.rpc.proxy.jdk.JdkProxyFactory;
import com.axon.dubbo.remoting.exchange.DubboCodec;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Step 09 测试用例
 *
 * 验证多 Provider 场景下的服务目录：
 * - 多个 Provider 同时注册
 * - Directory 正确展示全部 Invoker
 * - 静态目录（StaticDirectory）直连模式
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class ApiTest {

    /**
     * 测试1：静态目录（不经过注册中心，直接指定 Invoker 列表）
     */
    @Test
    public void testStaticDirectory() {
        ProxyFactory proxyFactory = new JdkProxyFactory();
        DubboCodec codec = new DubboCodec();

        // 构建 3 个 Provider Invoker（直连，不走注册中心）
        List<Invoker<IUserService>> invokers = new ArrayList<>();
        invokers.add(new DubboInvoker<>(IUserService.class,
                URL.builder().protocol("dubbo").host("10.0.0.1").port(20880)
                        .path(IUserService.class.getName()).build(), codec));
        invokers.add(new DubboInvoker<>(IUserService.class,
                URL.builder().protocol("dubbo").host("10.0.0.2").port(20880)
                        .path(IUserService.class.getName()).build(), codec));
        invokers.add(new DubboInvoker<>(IUserService.class,
                URL.builder().protocol("dubbo").host("10.0.0.3").port(20880)
                        .path(IUserService.class.getName()).build(), codec));

        // 创建静态目录
        StaticDirectory<IUserService> directory = new StaticDirectory<>(
                IUserService.class,
                URL.builder().path(IUserService.class.getName()).build(),
                invokers);

        System.out.println("========== StaticDirectory 测试 ==========");
        List<Invoker<IUserService>> list = directory.list(null);
        System.out.println("Directory 中 Invoker 数量: " + list.size());

        for (int i = 0; i < list.size(); i++) {
            System.out.println("  [" + i + "] " + list.get(i).getUrl().getAddress());
        }

        assert list.size() == 3 : "应有 3 个 Invoker";
        assert list.get(0).getUrl().getHost().equals("10.0.0.1");
        assert list.get(2).getUrl().getHost().equals("10.0.0.3");

        System.out.println("[测试通过] StaticDirectory 管理多提供者验证成功！");
    }

    /**
     * 测试2：多个 Provider 注册 → Directory 动态感知
     *
     * 核心场景：
     * - Provider1 上线 → Directory 有 1 个
     * - Provider2 上线 → Directory 有 2 个
     * - Provider3 上线 → Directory 有 3 个
     * - Provider2 下线 → Directory 有 2 个
     */
    @Test
    public void testMultiProviderDirectory() throws Exception {
        RegistryService registry = new LocalRegistry(
                URL.builder().protocol("registry").host("127.0.0.1").port(0).path("local").build());

        URL consumerUrl = URL.builder()
                .path(IUserService.class.getName()).addParameter("version", "1.0.0").build();

        // 创建 RegistryDirectory（自动订阅）
        RegistryDirectory<IUserService> directory = new RegistryDirectory<>(
                IUserService.class, consumerUrl, registry,
                url -> new DubboInvoker<>(IUserService.class, url, new DubboCodec()));

        Thread.sleep(100);
        System.out.println("========== 多 Provider 动态感知测试 ==========");
        System.out.println("[初始] Invoker 数量: " + directory.list(null).size());

        // Provider 1 上线
        URL p1 = buildProviderUrl("10.0.0.1", 20881);
        registry.register(p1); Thread.sleep(100);
        System.out.println("[P1上线] Invoker: " + directory.list(null).size());

        // Provider 2 上线
        URL p2 = buildProviderUrl("10.0.0.2", 20882);
        registry.register(p2); Thread.sleep(100);
        System.out.println("[P2上线] Invoker: " + directory.list(null).size());

        // Provider 3 上线
        URL p3 = buildProviderUrl("10.0.0.3", 20883);
        registry.register(p3); Thread.sleep(100);
        System.out.println("[P3上线] Invoker: " + directory.list(null).size());

        List<Invoker<IUserService>> all = directory.list(null);
        assert all.size() == 3 : "3 个 Provider 上线后应有 3 个 Invoker";
        System.out.println("Directory 中的全部 Invoker:");

        for (Invoker<IUserService> inv : all) {
            System.out.println("  → " + inv.getUrl().getAddress()
                    + " (可用: " + inv.isAvailable() + ")");
        }

        // Provider 2 下线
        registry.unregister(p2); Thread.sleep(100);
        System.out.println("[P2下线] Invoker: " + directory.list(null).size());
        assert directory.list(null).size() == 2 : "下线后应有 2 个";

        System.out.println("[测试通过] RegistryDirectory 动态感知多 Provider 验证成功！");

        directory.destroy();
    }

    /**
     * 测试3：端到端——两个 Provider，Consumer 通过 Directory 选择调用
     */
    @Test
    public void testMultiProviderRpcCall() throws Exception {
        RegistryService registry = new LocalRegistry(
                URL.builder().protocol("registry").host("127.0.0.1").port(0).path("local").build());

        Protocol protocol = new DubboProtocol(registry);
        ProxyFactory proxyFactory = new JdkProxyFactory();

        // Provider 1
        Invoker<IUserService> prov1 = proxyFactory.getInvoker(
                new UserServiceImpl(), IUserService.class, buildProviderUrl("127.0.0.1", 20891));
        protocol.export(prov1);

        // Provider 2
        Invoker<IUserService> prov2 = proxyFactory.getInvoker(
                new UserServiceImpl(), IUserService.class, buildProviderUrl("127.0.0.1", 20892));
        protocol.export(prov2);

        Thread.sleep(200);

        System.out.println("========== 多 Provider RPC 调用测试 ==========");

        // Consumer
        Invoker<IUserService> consumerInvoker = protocol.refer(
                IUserService.class, buildConsumerUrl());
        IUserService userService = proxyFactory.getProxy(consumerInvoker);

        // 调用（当前始终选第一个 Provider）
        User user = userService.getUser(1001L);
        assert "User_1001".equals(user.getName());
        System.out.println("[测试] 多 Provider 场景调用成功: " + user);

        // 验证 Directory 中有 2 个 Provider
        RegistryDirectory<IUserService> dir = ((DubboProtocol) protocol)
                .getDirectory(IUserService.class.getName() + ":1.0.0");
        if (dir != null) {
            System.out.println("[测试] Directory 中 Invoker 数量: " + dir.list(null).size());
            assert dir.list(null).size() == 2 : "应有 2 个 Provider";
        }

        System.out.println("[测试通过] 多 Provider 端到端验证成功！");
        ((DubboProtocol) protocol).destroy();
    }

    private URL buildProviderUrl(String host, int port) {
        return URL.builder()
                .protocol("dubbo").host(host).port(port)
                .path(IUserService.class.getName())
                .addParameter("version", "1.0.0").build();
    }

    private URL buildConsumerUrl() {
        return URL.builder().path(IUserService.class.getName())
                .addParameter("version", "1.0.0").build();
    }

    public static void main(String[] args) throws Exception {
        ApiTest test = new ApiTest();
        test.testStaticDirectory();
        test.testMultiProviderDirectory();
        test.testMultiProviderRpcCall();
        System.out.println("\n>>> Step 09 全部测试通过！");
    }
}
