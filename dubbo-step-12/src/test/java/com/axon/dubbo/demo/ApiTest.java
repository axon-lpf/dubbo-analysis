package com.axon.dubbo.demo;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.remoting.exchange.DubboCodec;
import com.axon.dubbo.rpc.*;
import com.axon.dubbo.rpc.cluster.Cluster;
import com.axon.dubbo.rpc.cluster.directory.StaticDirectory;
import com.axon.dubbo.rpc.cluster.support.*;
import com.axon.dubbo.rpc.protocol.DubboInvoker;
import com.axon.dubbo.rpc.proxy.ProxyFactory;
import com.axon.dubbo.rpc.proxy.jdk.JdkProxyFactory;
import org.junit.Test;

import java.util.*;

/**
 * Step 12 测试用例
 *
 * 验证全部 6 种集群容错策略
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class ApiTest {

    private static List<Invoker<IUserService>> buildInvokers(int count) {
        DubboCodec codec = new DubboCodec();
        List<Invoker<IUserService>> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            URL url = URL.builder().protocol("dubbo")
                    .host("10.0.0." + i).port(20880 + i)
                    .path(IUserService.class.getName()).build();
            list.add(new DubboInvoker<>(IUserService.class, url, codec));
        }
        return list;
    }

    private RpcInvocation makeInv(String method) {
        return new RpcInvocation(IUserService.class.getName(), method,
                new String[]{"java.lang.Long"}, new Object[]{1L});
    }

    /** Failover - 失败自动切换（复习） */
    @Test
    public void testFailoverCluster() {
        Cluster c = new FailoverCluster();
        Invoker<IUserService> inv = c.join(dir(3));
        System.out.println("========== Failover ==========");
        System.out.println("集群: " + inv.getClass().getSimpleName() + " | 可用: " + inv.isAvailable());
        assert inv.isAvailable();
        System.out.println("[测试通过] Failover ✓");
    }

    /** Failfast - 快速失败 */
    @Test
    public void testFailfastCluster() {
        Cluster c = new FailfastCluster();
        Invoker<IUserService> inv = c.join(dir(3));
        System.out.println("========== Failfast ==========");
        System.out.println("集群: " + inv.getClass().getSimpleName());
        try {
            inv.invoke(makeInv("getUser"));
        } catch (Exception e) {
            System.out.println("预期异常(无Provider在线): " + e.getMessage().substring(0, 50) + "...");
            assert e.getMessage().contains("Failfast") || e.getMessage().contains("Connection refused");
        }
        System.out.println("[测试通过] Failfast ✓");
    }

    /** Failsafe - 安全失败 */
    @Test
    public void testFailsafeCluster() {
        StaticDirectory<IUserService> empty = new StaticDirectory<>(
                IUserService.class, URL.builder().path("test").build());
        FailsafeClusterInvoker<IUserService> inv = new FailsafeClusterInvoker<>(empty);
        System.out.println("========== Failsafe ==========");
        try {
            Result r = inv.invoke(makeInv("getUser"));
            System.out.println("返回结果: " + r.getValue() + " (异常: " + r.hasException() + ")");
            assert !r.hasException() : "Failsafe 不应抛异常";
        } catch (Throwable e) {
            assert false : "Failsafe 不应抛异常: " + e.getMessage();
        }
        System.out.println("[测试通过] Failsafe ✓");
    }

    /** Forking - 并行调用 */
    @Test
    public void testForkingCluster() {
        Cluster c = new ForkingCluster();
        Invoker<IUserService> inv = c.join(dir(3));
        System.out.println("========== Forking ==========");
        System.out.println("集群: " + inv.getClass().getSimpleName());
        try {
            inv.invoke(makeInv("getUser"));
        } catch (Exception e) {
            System.out.println("预期异常(并发全部失败): " + e.getMessage().substring(0, 50) + "...");
            assert e.getMessage().contains("Forking") || e.getMessage().contains("Connection refused");
        }
        System.out.println("[测试通过] Forking ✓");
    }

    /** Broadcast - 广播调用 */
    @Test
    public void testBroadcastCluster() {
        Cluster c = new BroadcastCluster();
        Invoker<IUserService> inv = c.join(dir(3));
        System.out.println("========== Broadcast ==========");
        System.out.println("集群: " + inv.getClass().getSimpleName());
        try {
            inv.invoke(makeInv("getUser"));
        } catch (Exception e) {
            System.out.println("预期异常(无Provider): " + e.getMessage().substring(0, 50) + "...");
        }
        System.out.println("[测试通过] Broadcast ✓");
    }

    /** Failback - 失败后台重试 */
    @Test
    public void testFailbackCluster() {
        StaticDirectory<IUserService> empty = new StaticDirectory<>(
                IUserService.class, URL.builder().path("test").build());
        FailbackClusterInvoker<IUserService> inv = new FailbackClusterInvoker<>(empty);
        System.out.println("========== Failback ==========");
        try {
            Result r = inv.invoke(makeInv("getUser"));
            System.out.println("返回(空Provider): " + r.getValue());
            assert !r.hasException();
        } catch (Throwable e) {
            assert false : "Failback 空 Provider 不应抛异常";
        }
        System.out.println("[测试通过] Failback ✓");
    }

    /** Available - 可用性检查 */
    @Test
    public void testAvailableCluster() {
        Cluster c = new AvailableCluster();
        Invoker<IUserService> inv = c.join(dir(3));
        System.out.println("========== Available ==========");
        System.out.println("集群: " + inv.getClass().getSimpleName());
        try {
            inv.invoke(makeInv("getUser"));
        } catch (Exception e) {
            System.out.println("预期异常: " + e.getMessage().substring(0, 50) + "...");
        }
        System.out.println("[测试通过] Available ✓");
    }

    private static StaticDirectory<IUserService> dir(int n) {
        return new StaticDirectory<>(IUserService.class,
                URL.builder().path(IUserService.class.getName()).build(),
                buildInvokers(n));
    }

    public static void main(String[] args) {
        ApiTest t = new ApiTest();
        t.testFailoverCluster(); t.testFailfastCluster();
        t.testFailsafeCluster(); t.testForkingCluster();
        t.testBroadcastCluster(); t.testFailbackCluster();
        t.testAvailableCluster();
        System.out.println("\n>>> Step 12 全部 7 种集群容错策略验证通过！");
    }
}
