package com.axon.dubbo.demo;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.remoting.exchange.DubboCodec;
import com.axon.dubbo.rpc.Invoker;
import com.axon.dubbo.rpc.RpcInvocation;
import com.axon.dubbo.rpc.cluster.LoadBalance;
import com.axon.dubbo.rpc.cluster.loadbalance.ConsistentHashLoadBalance;
import com.axon.dubbo.rpc.cluster.loadbalance.LeastActiveLoadBalance;
import com.axon.dubbo.rpc.cluster.loadbalance.RandomLoadBalance;
import com.axon.dubbo.rpc.cluster.loadbalance.RoundRobinLoadBalance;
import com.axon.dubbo.rpc.protocol.DubboInvoker;
import org.junit.Test;

import java.util.*;

/**
 * Step 10 测试用例
 *
 * 验证 4 种负载均衡策略：
 * - RandomLoadBalance（加权随机）
 * - RoundRobinLoadBalance（加权轮询）
 * - LeastActiveLoadBalance（最少活跃）
 * - ConsistentHashLoadBalance（一致性哈希）
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class ApiTest {

    /** 构建 3 个不同权重的 Invoker */
    private List<Invoker<IUserService>> buildInvokers(int... weights) {
        DubboCodec codec = new DubboCodec();
        List<Invoker<IUserService>> list = new ArrayList<>();
        for (int i = 0; i < weights.length; i++) {
            URL url = URL.builder()
                    .protocol("dubbo").host("10.0.0." + (i + 1)).port(20880 + i)
                    .path(IUserService.class.getName())
                    .addParameter("weight", String.valueOf(weights[i]))
                    .addParameter("active", String.valueOf(i == 0 ? 0 : 5)) // P1:0 P2:5 P3:5
                    .build();
            list.add(new DubboInvoker<>(IUserService.class, url, codec));
        }
        return list;
    }

    private RpcInvocation makeInvocation(String method, Object... args) {
        String[] types = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i] != null ? args[i].getClass().getName() : "java.lang.Object";
        }
        return new RpcInvocation(IUserService.class.getName(), method, types, args);
    }

    /**
     * 测试1：加权随机负载均衡
     */
    @Test
    public void testRandomLoadBalance() {
        LoadBalance lb = new RandomLoadBalance();
        // Provider: A(100) B(200) C(300)  → 总权重 600
        List<Invoker<IUserService>> invokers = buildInvokers(100, 200, 300);
        RpcInvocation inv = makeInvocation("getUser", 1L);

        // 统计 6000 次调用的分布
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 6000; i++) {
            Invoker<IUserService> selected = lb.select(invokers,
                    invokers.get(0).getUrl(), inv);
            String addr = selected.getUrl().getAddress();
            counts.merge(addr, 1, Integer::sum);
        }

        System.out.println("========== RandomLoadBalance（加权随机）==========");
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            System.out.println("  " + e.getKey() + ": " + e.getValue() + " 次");
        }

        // 权重比例接近 1:2:3
        int c1 = counts.getOrDefault("10.0.0.1:20880", 0);
        int c2 = counts.getOrDefault("10.0.0.2:20881", 0);
        int c3 = counts.getOrDefault("10.0.0.3:20882", 0);
        assert c3 > c2 : "权重 300 应多于 200";
        assert c2 > c1 : "权重 200 应多于 100";

        System.out.println("[测试通过] 加权随机分布符合权重比例！");
    }

    /**
     * 测试2：加权轮询负载均衡
     */
    @Test
    public void testRoundRobinLoadBalance() {
        LoadBalance lb = new RoundRobinLoadBalance();
        // Provider: A(5) B(1) C(1) → 共 7 次，应 A=5 B=1 C=1
        List<Invoker<IUserService>> invokers = buildInvokers(5, 1, 1);
        RpcInvocation inv = makeInvocation("getUser", 1L);

        System.out.println("========== RoundRobinLoadBalance（加权轮询）==========");
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 7; i++) {
            Invoker<IUserService> selected = lb.select(invokers,
                    invokers.get(0).getUrl(), inv);
            String addr = selected.getUrl().getAddress();
            counts.merge(addr, 1, Integer::sum);
            System.out.println("  第" + (i + 1) + "次 → " + addr);
        }

        assert counts.getOrDefault("10.0.0.1:20880", 0) == 5 : "权重5应有5次";
        assert counts.getOrDefault("10.0.0.2:20881", 0) == 1 : "权重1应有1次";
        assert counts.getOrDefault("10.0.0.3:20882", 0) == 1 : "权重1应有1次";

        System.out.println("[测试通过] 加权轮询分布完美匹配 5:1:1！");
    }

    /**
     * 测试3：最少活跃负载均衡
     */
    @Test
    public void testLeastActiveLoadBalance() {
        LoadBalance lb = new LeastActiveLoadBalance();
        // P1: active=0, P2: active=5, P3: active=5
        // 应总是选 P1（活跃数最少）
        List<Invoker<IUserService>> invokers = buildInvokers(100, 100, 100);
        RpcInvocation inv = makeInvocation("getUser", 1L);

        System.out.println("========== LeastActiveLoadBalance ==========");
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 20; i++) {
            Invoker<IUserService> selected = lb.select(invokers,
                    invokers.get(0).getUrl(), inv);
            counts.merge(selected.getUrl().getAddress(), 1, Integer::sum);
        }

        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            System.out.println("  " + e.getKey() + ": " + e.getValue() + " 次");
        }

        // P1 (active=0) 应被选最多
        int c1 = counts.getOrDefault("10.0.0.1:20880", 0);
        assert c1 == 20 : "活跃数最少的 Provider(active=0)应被选全部 20 次";

        System.out.println("[测试通过] 最少活跃正确选中最空闲的 Provider！");
    }

    /**
     * 测试4：一致性哈希负载均衡
     */
    @Test
    public void testConsistentHashLoadBalance() {
        LoadBalance lb = new ConsistentHashLoadBalance();
        List<Invoker<IUserService>> invokers = buildInvokers(100, 100, 100);

        System.out.println("========== ConsistentHashLoadBalance ==========");

        // 相同参数 → 总是路由到同一个 Provider
        String first = null;
        for (int i = 0; i < 10; i++) {
            RpcInvocation inv = makeInvocation("getUser", 1001L);
            Invoker<IUserService> selected = lb.select(invokers,
                    invokers.get(0).getUrl(), inv);
            if (first == null) first = selected.getUrl().getAddress();
            assert first.equals(selected.getUrl().getAddress())
                    : "相同参数应始终路由到同一 Provider";
        }
        System.out.println("  相同参数(1001L) 10 次 → 始终命中 " + first + " ✓");

        // 不同参数 → 可能路由到不同 Provider
        Set<String> targets = new HashSet<>();
        for (long id = 1; id <= 50; id++) {
            RpcInvocation inv = makeInvocation("getUser", id);
            Invoker<IUserService> selected = lb.select(invokers,
                    invokers.get(0).getUrl(), inv);
            targets.add(selected.getUrl().getAddress());
        }
        System.out.println("  不同参数(1-50) 50 次 → 命中 " + targets.size() + " 个不同 Provider");
        assert targets.size() >= 2 : "不同参数应路由到多个 Provider";

        System.out.println("[测试通过] 一致性哈希验证成功！");
    }

    /**
     * 测试5：只有 1 个 Provider 时直接返回
     */
    @Test
    public void testSingleProvider() {
        RpcInvocation inv = makeInvocation("getUser", 1L);
        List<Invoker<IUserService>> single = buildInvokers(100).subList(0, 1);

        assert new RandomLoadBalance().select(single, single.get(0).getUrl(), inv)
                .getUrl().getHost().equals("10.0.0.1");
        assert new RoundRobinLoadBalance().select(single, single.get(0).getUrl(), inv)
                .getUrl().getHost().equals("10.0.0.1");
        assert new LeastActiveLoadBalance().select(single, single.get(0).getUrl(), inv)
                .getUrl().getHost().equals("10.0.0.1");
        assert new ConsistentHashLoadBalance().select(single, single.get(0).getUrl(), inv)
                .getUrl().getHost().equals("10.0.0.1");

        System.out.println("[测试通过] 单 Provider 时 4 种策略都直接返回！");
    }

    public static void main(String[] args) {
        ApiTest t = new ApiTest();
        t.testSingleProvider();
        t.testRandomLoadBalance();
        t.testRoundRobinLoadBalance();
        t.testLeastActiveLoadBalance();
        t.testConsistentHashLoadBalance();
        System.out.println("\n>>> Step 10 全部测试通过！");
    }
}
