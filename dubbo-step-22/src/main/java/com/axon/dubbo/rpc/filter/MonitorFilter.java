package com.axon.dubbo.rpc.filter;

import com.axon.dubbo.common.extension.Activate;
import com.axon.dubbo.rpc.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 监控过滤器
 *
 * 收集每次 RPC 调用的统计数据：
 * - 调用次数
 * - 成功次数 / 失败次数
 * - 总耗时 / 平均耗时 / 最大耗时 / 最小耗时
 *
 * 统计数据按 "接口名.方法名" 分组。
 *
 * @Activate 在 Provider 和 Consumer 两端都激活，
 * 分别统计两端的调用情况。
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
@Activate(group = {"provider", "consumer"}, order = Integer.MAX_VALUE)
public class MonitorFilter implements Filter {

    /**
     * 统计数据存储
     * Key: "接口名.方法名"
     */
    private static final Map<String, MethodStats> STATS = new ConcurrentHashMap<>();

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) {
        long start = System.currentTimeMillis();
        boolean success = true;

        try {
            Result result = invoker.invoke(invocation);
            if (result.hasException()) {
                success = false;
            }
            return result;
        } catch (Throwable e) {
            success = false;
            throw e;
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            String key = invocation.getServiceName() + "." + invocation.getMethodName();
            STATS.computeIfAbsent(key, k -> new MethodStats()).record(success, elapsed);
        }
    }

    /**
     * 获取所有统计摘要
     */
    public static String getSummary() {
        List<Map.Entry<String, MethodStats>> sorted = new ArrayList<>(STATS.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue().totalCount(), a.getValue().totalCount()));

        StringBuilder sb = new StringBuilder();
        sb.append("\n========== Monitor 统计 ==========\n");
        for (Map.Entry<String, MethodStats> entry : sorted) {
            sb.append(entry.getValue().summary(entry.getKey())).append("\n");
        }
        sb.append("==================================");
        return sb.toString();
    }

    /**
     * 清空统计数据
     */
    public static void reset() {
        STATS.clear();
    }

    // ==================== 内部类 ====================

    static class MethodStats {
        private final AtomicLong totalCount = new AtomicLong();
        private final AtomicLong successCount = new AtomicLong();
        private final AtomicLong failCount = new AtomicLong();
        private final AtomicLong totalElapsed = new AtomicLong();
        private volatile long maxElapsed = 0;
        private volatile long minElapsed = Long.MAX_VALUE;

        synchronized void record(boolean success, long elapsed) {
            totalCount.incrementAndGet();
            if (success) successCount.incrementAndGet();
            else failCount.incrementAndGet();
            totalElapsed.addAndGet(elapsed);
            if (elapsed > maxElapsed) maxElapsed = elapsed;
            if (elapsed < minElapsed) minElapsed = elapsed;
        }

        long totalCount() { return totalCount.get(); }

        String summary(String key) {
            long total = totalCount.get();
            long success = successCount.get();
            long fail = failCount.get();
            long elapsed = totalElapsed.get();
            return String.format("  %-50s  总:%4d  成功:%4d  失败:%2d  "
                            + "平均:%3dms  最大:%3dms  最小:%3dms",
                    key, total, success, fail,
                    total > 0 ? elapsed / total : 0,
                    maxElapsed, total > 0 ? minElapsed : 0);
        }
    }
}
