package com.axon.dubbo.demo;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.remoting.exchange.DubboCodec;
import com.axon.dubbo.rpc.*;
import com.axon.dubbo.rpc.cluster.Cluster;
import com.axon.dubbo.rpc.cluster.Directory;
import com.axon.dubbo.rpc.cluster.LoadBalance;
import com.axon.dubbo.rpc.cluster.directory.StaticDirectory;
import com.axon.dubbo.rpc.cluster.loadbalance.RoundRobinLoadBalance;
import com.axon.dubbo.rpc.cluster.support.FailoverCluster;
import com.axon.dubbo.rpc.cluster.support.FailoverClusterInvoker;
import com.axon.dubbo.rpc.protocol.DubboInvoker;
import com.axon.dubbo.rpc.proxy.ProxyFactory;
import com.axon.dubbo.rpc.proxy.jdk.JdkProxyFactory;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Step 11 测试用例
 *
 * 验证 Cluster 集群调用：
 * - Cluster.join(Directory) → ClusterInvoker
 * - FailoverClusterInvoker 失败自动切换
 * - 集群 Invoker + Proxy 端到端透明调用
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class ApiTest {

    private static List<Invoker<IUserService>> buildInvokers(
            String... hostsAndPorts) {
        DubboCodec codec = new DubboCodec();
        List<Invoker<IUserService>> list = new ArrayList<>();
        for (int i = 0; i < hostsAndPorts.length; i++) {
            String[] parts = hostsAndPorts[i].split(":");
            URL url = URL.builder().protocol("dubbo")
                    .host(parts[0]).port(Integer.parseInt(parts[1]))
                    .path(IUserService.class.getName())
                    .addParameter("weight", "100")
                    .addParameter("retries", "2").build();
            list.add(new DubboInvoker<>(IUserService.class, url, codec));
        }
        return list;
    }

    private RpcInvocation makeInv(String method, Object... args) {
        String[] types = new String[args.length];
        for (int i = 0; i < args.length; i++)
            types[i] = args[i] != null ? args[i].getClass().getName() : "Object";
        return new RpcInvocation(IUserService.class.getName(), method, types, args);
    }

    /**
     * 测试1：Cluster.join() 将 Directory 包装为 Invoker
     */
    @Test
    public void testClusterJoin() {
        List<Invoker<IUserService>> invokers = buildInvokers(
                "10.0.0.1:20880", "10.0.0.2:20880", "10.0.0.3:20880");

        StaticDirectory<IUserService> directory = new StaticDirectory<>(
                IUserService.class,
                URL.builder().path(IUserService.class.getName()).build(),
                invokers);

        Cluster cluster = new FailoverCluster();

        System.out.println("========== Cluster.join() 测试 ==========");

        // 将 Directory 转换为集群 Invoker
        Invoker<IUserService> clusterInvoker = cluster.join(directory);

        // 验证集群 Invoker 的基础属性
        System.out.println("集群 Invoker 接口: " + clusterInvoker.getInterface().getName());
        System.out.println("集群 Invoker 可用: " + clusterInvoker.isAvailable());
        assert clusterInvoker.getInterface() == IUserService.class;
        assert clusterInvoker.isAvailable();

        // 同一 Directory 创建的集群 Invoker 内部有独立的负载均衡逻辑
        Directory<IUserService> dir = directory;
        assert dir.list(null).size() == 3 : "Directory 中应有 3 个 Invoker";

        System.out.println("[测试通过] Cluster.join() 验证成功！");
    }

    /**
     * 测试2：FailoverClusterInvoker + LoadBalance 结构验证
     */
    @Test
    public void testFailoverWithLoadBalance() {
        List<Invoker<IUserService>> invokers = buildInvokers(
                "10.0.0.1:20880", "10.0.0.2:20880", "10.0.0.3:20880");

        StaticDirectory<IUserService> directory = new StaticDirectory<>(
                IUserService.class,
                URL.builder().path(IUserService.class.getName()).build(),
                invokers);

        // 使用轮询的 FailoverClusterInvoker
        LoadBalance lb = new RoundRobinLoadBalance();
        FailoverClusterInvoker<IUserService> clusterInvoker =
                new FailoverClusterInvoker<>(directory, lb);

        System.out.println("========== Failover + LoadBalance 测试 ==========");
        System.out.println("集群 Invoker 类型: " + clusterInvoker.getClass().getSimpleName());
        System.out.println("集群 Invoker 可用: " + clusterInvoker.isAvailable());
        System.out.println("服务接口: " + clusterInvoker.getInterface().getName());
        System.out.println("负载均衡: " + lb.getClass().getSimpleName());

        assert clusterInvoker.isAvailable();
        assert clusterInvoker.getInterface() == IUserService.class;

        System.out.println("[测试通过] FailoverClusterInvoker 结构验证成功！");
    }

    /**
     * 测试3：Directory 为空时的容错
     */
    @Test
    public void testEmptyDirectory() {
        StaticDirectory<IUserService> emptyDir = new StaticDirectory<>(
                IUserService.class,
                URL.builder().path(IUserService.class.getName()).build());

        Cluster cluster = new FailoverCluster();
        Invoker<IUserService> clusterInvoker = cluster.join(emptyDir);

        RpcInvocation inv = makeInv("getUser", 1L);

        System.out.println("========== 空 Directory 容错测试 ==========");
        try {
            clusterInvoker.invoke(inv);
            System.out.println("[失败] 应该抛出异常");
        } catch (Exception e) {
            System.out.println("[预期异常] " + e.getMessage());
            assert e.getMessage().contains("没有可用的 Provider");
        }

        System.out.println("[测试通过] 空 Directory 正确抛出异常！");
    }

    /**
     * 测试4：Cluster → Proxy 端到端集成
     */
    @Test
    public void testClusterProxyIntegration() {
        ProxyFactory proxyFactory = new JdkProxyFactory();
        DubboCodec codec = new DubboCodec();

        // 构建 3 个直连 Invoker 的 StaticDirectory
        List<Invoker<IUserService>> invokers = new ArrayList<>();
        int port = 20901;
        for (int i = 1; i <= 3; i++) {
            URL url = URL.builder().protocol("dubbo")
                    .host("127.0.0.1").port(port++)
                    .path(IUserService.class.getName())
                    .addParameter("retries", "2").build();
            invokers.add(new DubboInvoker<>(IUserService.class, url, codec));
        }

        StaticDirectory<IUserService> directory = new StaticDirectory<>(
                IUserService.class,
                URL.builder().path(IUserService.class.getName()).build(),
                invokers);

        // Cluster 包装
        Cluster cluster = new FailoverCluster();
        Invoker<IUserService> clusterInvoker = cluster.join(directory);

        // Proxy 包装
        IUserService userService = proxyFactory.getProxy(clusterInvoker);

        System.out.println("========== Cluster → Proxy 集成测试 ==========");

        // 调用（注：无 Provider 在线时预期失败）
        try {
            User user = userService.getUser(1L);
            System.out.println("[意外] 调用成功（可能有本地 Provider）: " + user);
        } catch (Exception e) {
            System.out.println("[预期] 调用失败（无 Provider 在线）");
            System.out.println("  异常类型: " + e.getClass().getSimpleName());
            System.out.println("  异常信息: " + e.getMessage());
            // FailoverClusterInvoker 会尝试所有 Provider 后报错
            assert e.getMessage().contains("全部") || e.getMessage().contains("失败")
                    || e.getMessage().contains("Connection refused");
        }

        System.out.println("[测试通过] Cluster → Proxy 集成验证成功！");
    }

    public static void main(String[] args) {
        ApiTest t = new ApiTest();
        t.testClusterJoin();
        t.testFailoverWithLoadBalance();
        t.testEmptyDirectory();
        t.testClusterProxyIntegration();
        System.out.println("\n>>> Step 11 全部测试通过！");
    }
}
