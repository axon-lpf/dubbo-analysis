package com.axon.dubbo.demo;

import com.axon.dubbo.common.Constants;
import com.axon.dubbo.common.extension.ExtensionLoader;
import com.axon.dubbo.common.serialize.Serialization;
import com.axon.dubbo.remoting.exchange.DubboCodec;
import com.axon.dubbo.remoting.transport.socket.Request;
import com.axon.dubbo.remoting.transport.socket.Response;
import org.junit.Test;

import java.io.Serializable;
import java.util.*;

/**
 * Step 18 测试用例 —— 多序列化扩展
 *
 * 验证：
 * 1. SPI 加载 4 种序列化实现
 * 2. 序列化往返测试（4 种）
 * 3. 序列化体积对比
 * 4. 协议头序列化标志位动态切换
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class ApiTest {

    /** 测试数据 —— 模拟真实的 RPC 调用对象 */
    private static Request buildTestRequest() {
        return new Request(1L, IUserService.class.getName(),
                "getUser", new String[]{"java.lang.Long"}, new Object[]{1001L});
    }

    /** 测试数据 —— 模拟嵌套对象 */
    private static TestOrder buildTestOrder() {
        TestOrder order = new TestOrder();
        order.orderId = "ORD-2024-001";
        order.userId = 1001L;
        order.items = Arrays.asList(
                new TestItem("SKU-001", "商品A", 3, 99.9),
                new TestItem("SKU-002", "商品B", 1, 199.9),
                new TestItem("SKU-003", "商品C", 5, 49.9));
        order.totalAmount = 849.3;
        return order;
    }

    /**
     * 测试1：SPI 加载全部序列化实现
     */
    @Test
    public void testSerializationSPI() {
        ExtensionLoader<Serialization> loader = ExtensionLoader.getExtensionLoader(Serialization.class);
        Set<String> names = loader.getSupportedExtensions();

        System.out.println("========== 序列化 SPI ==========");
        for (String name : names) {
            Serialization s = loader.getExtension(name);
            System.out.println("  " + name + " → " + s.getClass().getSimpleName());
        }

        assert names.contains("java") : "应有 JDK 序列化";
        assert names.contains("hessian2") : "应有 Hessian2 序列化";
        assert names.contains("fastjson") : "应有 Fastjson 序列化";
        assert names.contains("kryo") : "应有 Kryo 序列化";

        System.out.println("[测试通过] 4 种序列化全部通过 SPI 加载！");
    }

    /**
     * 测试2：序列化往返测试 + 体积对比
     */
    @Test
    public void testSerializationRoundTrip() throws Exception {
        ExtensionLoader<Serialization> loader = ExtensionLoader.getExtensionLoader(Serialization.class);
        TestOrder original = buildTestOrder();

        System.out.println("========== 序列化往返 + 体积对比 ==========");
        System.out.println("原始对象: " + original);
        System.out.println();

        Map<String, Integer> sizes = new LinkedHashMap<>();
        for (String name : Arrays.asList("java", "hessian2", "fastjson", "kryo")) {
            Serialization s = loader.getExtension(name);

            // 序列化
            byte[] bytes = s.serialize(original);
            sizes.put(name, bytes.length);

            // 反序列化
            TestOrder restored = s.deserialize(bytes, TestOrder.class);

            System.out.printf("  %-10s → %5d bytes | 还原: orderId=%s items=%d total=%.1f\n",
                    name, bytes.length,
                    restored.orderId,
                    restored.items != null ? restored.items.size() : 0,
                    restored.totalAmount);

            assert original.orderId.equals(restored.orderId) : name + " 数据不完整";
        }

        // 体积对比
        int javaSize = sizes.get("java");
        System.out.println("\n体积对比（以 JDK 为基准 100%）:");
        for (Map.Entry<String, Integer> e : sizes.entrySet()) {
            double pct = e.getValue() * 100.0 / javaSize;
            System.out.printf("  %-10s %5d bytes (%5.1f%%)\n", e.getKey(), e.getValue(), pct);
        }

        // Kryo 应该是最小的
        assert sizes.get("kryo") < sizes.get("java") : "Kryo 应比 JDK 小";

        System.out.println("[测试通过] 4 种序列化往返全部成功！");
    }

    /**
     * 测试3：协议头序列化标志位动态切换
     */
    @Test
    public void testCodecWithSerializationSwitch() throws Exception {
        Request request = buildTestRequest();

        System.out.println("========== 协议头序列化标志位 ==========");

        // 不同序列化方式编码
        for (byte serId = 0; serId <= 3; serId++) {
            DubboCodec codec = new DubboCodec(serId);
            byte[] encoded = codec.encode(request);

            // 检查协议头 Flag 字节的低 3 位
            byte flag = encoded[2];
            byte actualSerId = (byte) (flag & 0x07);

            // 解码验证
            Request decoded = (Request) codec.decode(encoded);

            System.out.printf("  序列化ID=%d (0x%s) → %5d bytes | Flag=0x%02X | 解码: %s\n",
                    serId, Integer.toHexString(serId), encoded.length, flag,
                    decoded.getMethodName() + "(" + decoded.getArguments()[0] + ")");

            assert actualSerId == serId : "协议头序列化ID 应匹配";
            assert decoded.getId() == request.getId() : "ID 应一致";
            assert decoded.getMethodName().equals("getUser") : "方法名应一致";
        }

        System.out.println("[测试通过] 协议头序列化标志位 4 种全部验证！");
    }

    /**
     * 测试4：默认序列化（@SPI("java")）
     */
    @Test
    public void testDefaultSerialization() {
        Serialization def = ExtensionLoader.getExtensionLoader(Serialization.class)
                .getDefaultExtension();
        System.out.println("========== 默认序列化 ==========");
        System.out.println("默认: " + def.getClass().getSimpleName());
        assert def instanceof com.axon.dubbo.common.serialize.java.JavaSerialization;
        System.out.println("[测试通过] @SPI(\"java\") 默认序列化验证！");
    }

    public static void main(String[] args) throws Exception {
        ApiTest t = new ApiTest();
        t.testSerializationSPI();
        t.testDefaultSerialization();
        t.testSerializationRoundTrip();
        t.testCodecWithSerializationSwitch();
        System.out.println("\n>>> Step 18 全部测试通过！");
    }

    // ==================== 测试数据类 ====================

    public static class TestOrder implements Serializable {
        private static final long serialVersionUID = 1L;
        String orderId;
        Long userId;
        List<TestItem> items;
        double totalAmount;

        public String getOrderId() { return orderId; }
        public void setOrderId(String v) { orderId = v; }
        public Long getUserId() { return userId; }
        public void setUserId(Long v) { userId = v; }
        public List<TestItem> getItems() { return items; }
        public void setItems(List<TestItem> v) { items = v; }
        public double getTotalAmount() { return totalAmount; }
        public void setTotalAmount(double v) { totalAmount = v; }

        @Override
        public String toString() {
            return "Order{id=" + orderId + ", uid=" + userId
                    + ", items=" + (items != null ? items.size() : 0)
                    + ", total=" + totalAmount + "}";
        }
    }

    public static class TestItem implements Serializable {
        private static final long serialVersionUID = 1L;
        String sku;
        String name;
        int quantity;
        double price;

        TestItem() {}
        TestItem(String sku, String name, int qty, double price) {
            this.sku = sku; this.name = name; this.quantity = qty; this.price = price;
        }
        public String getSku() { return sku; } public void setSku(String v) { sku = v; }
        public String getName() { return name; } public void setName(String v) { name = v; }
        public int getQuantity() { return quantity; } public void setQuantity(int v) { quantity = v; }
        public double getPrice() { return price; } public void setPrice(double v) { price = v; }
        @Override
        public String toString() {
            return "Item{" + sku + " " + name + " x" + quantity + " @" + price + "}";
        }
    }
}
