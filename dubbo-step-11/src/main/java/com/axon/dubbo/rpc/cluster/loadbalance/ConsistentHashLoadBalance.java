package com.axon.dubbo.rpc.cluster.loadbalance;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.rpc.Invocation;
import com.axon.dubbo.rpc.Invoker;
import com.axon.dubbo.rpc.cluster.AbstractLoadBalance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一致性哈希负载均衡
 *
 * 算法：
 * 1. 为每个 Invoker 创建多个虚拟节点（默认 160 个）
 * 2. 将所有虚拟节点映射到 TreeMap（哈希环）
 * 3. 对方法参数（第一个参数）计算哈希值
 * 4. 在 TreeMap 中查找 ≥ 该哈希值的第一个节点（顺时针查找）
 * 5. 如果没有更大的节点 → 返回第一个节点（环形）
 *
 * 优点：相同参数的请求总是路由到同一个 Provider
 *      适合有状态服务、缓存亲和性场景
 *
 * 缺点：Provider 上下线时部分请求会重新分配
 *
 * 对应官方源码：org.apache.dubbo.rpc.cluster.loadbalance.ConsistentHashLoadBalance
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class ConsistentHashLoadBalance extends AbstractLoadBalance {

    /**
     * 每个服务方法维护自己的哈希环
     */
    private final ConcurrentHashMap<String, ConsistentHashSelector<?>> selectorMap = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        String key = invokers.get(0).getUrl().getServiceKey() + "." + invocation.getMethodName();

        // 哈希环的 identityHashCode 用于检测 Invoker 列表是否变化
        int identityHashCode = System.identityHashCode(invokers);

        ConsistentHashSelector<T> selector = (ConsistentHashSelector<T>) selectorMap.get(key);
        if (selector == null || selector.identityHashCode != identityHashCode) {
            selector = new ConsistentHashSelector<>(invokers, identityHashCode);
            selectorMap.put(key, selector);
        }

        return selector.select(invocation);
    }

    /**
     * 一致性哈希选择器
     * 维护一个 Invoker 列表对应的哈希环
     */
    static class ConsistentHashSelector<T> {

        /** 每个 Invoker 的虚拟节点数 */
        private static final int VIRTUAL_NODES = 160;

        /** 哈希环（TreeMap 天然有序） */
        private final TreeMap<Long, Invoker<T>> ring = new TreeMap<>();

        /** Invoker 列表的 hashCode，用于检测变化 */
        private final int identityHashCode;

        ConsistentHashSelector(List<Invoker<T>> invokers, int identityHashCode) {
            this.identityHashCode = identityHashCode;

            for (Invoker<T> invoker : invokers) {
                String address = invoker.getUrl().getAddress();
                for (int i = 0; i < VIRTUAL_NODES / 4; i++) {
                    // 每组 4 个虚拟节点，用不同前缀区分
                    byte[] digest = md5(address + i);
                    for (int h = 0; h < 4; h++) {
                        long hash = hash(digest, h);
                        ring.put(hash, invoker);
                    }
                }
            }
        }

        /**
         * 根据调用参数选择 Invoker
         */
        Invoker<T> select(Invocation invocation) {
            // 用第一个参数的 hashCode 作为 key
            String key = buildArgumentKey(invocation);
            byte[] digest = md5(key);
            long hash = hash(digest, 0);

            // 顺时针查找
            Map.Entry<Long, Invoker<T>> entry = ring.ceilingEntry(hash);
            if (entry == null) {
                // 环形结构：没有更大的 → 取第一个
                entry = ring.firstEntry();
            }
            return entry.getValue();
        }

        private String buildArgumentKey(Invocation invocation) {
            Object[] args = invocation.getArguments();
            if (args != null && args.length > 0 && args[0] != null) {
                return args[0].toString();
            }
            return invocation.getMethodName();
        }

        /** MD5 摘要 */
        static byte[] md5(String value) {
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                return md.digest(value.getBytes(StandardCharsets.UTF_8));
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }

        /** 从 MD5 摘要中提取 4 个 long 哈希值 */
        static long hash(byte[] digest, int idx) {
            return ((long) (digest[3 + idx * 4] & 0xFF) << 24
                    | (long) (digest[2 + idx * 4] & 0xFF) << 16
                    | (long) (digest[1 + idx * 4] & 0xFF) << 8
                    | (long) (digest[idx * 4] & 0xFF))
                    & 0xFFFFFFFFL;
        }
    }
}
