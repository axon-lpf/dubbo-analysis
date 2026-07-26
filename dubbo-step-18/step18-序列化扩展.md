# Step 18：序列化扩展（Hessian2 / Fastjson / Kryo）

## 一、本步骤解决的问题

**核心问题：JDK 序列化体积大、性能差，如何支持多种序列化并按需切换？**

Step 02 以来一直使用 JDK 原生序列化。Step 18 将 Serialization 升级为 @SPI 扩展点，新增 Hessian2、Fastjson、Kryo 三种实现，通过协议头标志位动态选择。

## 二、新增了哪些能力

- ✅ `Serialization` 升级为 @SPI("java")
- ✅ `Hessian2Serialization` — 二进制、体积小、跨语言
- ✅ `FastJsonSerialization` — JSON 可读、快速
- ✅ `KryoSerialization` — 体积最小、速度最快
- ✅ `DubboCodec` 升级 — 协议头标志位 + SPI 动态加载序列化

## 三、核心类一览

| 类名 | 序列化方式 | 特点 |
|------|----------|------|
| `JavaSerialization` | JDK ObjectStream | 体积大、慢、仅 Java |
| `Hessian2Serialization` | Hessian2 二进制 | 体积小、快、跨语言 |
| `FastJsonSerialization` | JSON 文本 | 可读、快、中文友好 |
| `KryoSerialization` | Kryo 二进制 | 体积最小、最快、非线程安全 |

## 四、核心原理剖析

### 4.1 协议头序列化标志位

```
Dubbo 协议头 Flag 字节（第 3 字节）：

Bit:  7      6      5 4 3    2  1  0
      │      │      │        └──┴──┴── 序列化 ID
      │      │      └────────────── 保留
      │      └───────────────────── 单向标志
      └──────────────────────────── 请求/响应

序列化 ID：
  0 = JDK（默认）
  1 = Hessian2
  2 = Fastjson
  3 = Kryo
```

### 4.2 SPI 动态加载

```java
// 协议头解码时读取序列化 ID
byte serId = (byte) (flag & 0x07);

// 通过 SPI 加载对应实现
String[] NAMES = {"java", "hessian2", "fastjson", "kryo"};
Serialization ser = ExtensionLoader.getExtensionLoader(Serialization.class)
        .getExtension(NAMES[serId]);

// 使用加载的实现完成编解码
byte[] body = ser.serialize(request);
Request req = ser.deserialize(bytes, Request.class);
```

### 4.3 体积对比

以包含嵌套对象（订单 + 3 个商品项）的测试数据为例：

| 序列化 | 体积 | 相对 JDK |
|--------|------|---------|
| JDK | ~900 bytes | 100% |
| Hessian2 | ~350 bytes | ~39% |
| Fastjson | ~450 bytes | ~50% |
| Kryo | ~150 bytes | ~17% |

### 4.4 Kryo 的线程安全处理

```java
public class KryoSerialization implements Serialization {
    // Kryo 非线程安全 → ThreadLocal 隔离
    private static final ThreadLocal<Kryo> KRYO = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false);
        return kryo;
    });
}
```

## 五、面试常见问法

**Q: Dubbo 默认用什么序列化？为什么？**
A: Dubbo 默认用 Hessian2。原因：体积小、速度快、跨语言支持好。JDK 序列化体积大、性能差且只能 Java 使用。

**Q: Kryo 序列化有什么优缺点？**
A: 优点是体积最小、速度最快。缺点是需要提前注册类，且 Kryo 实例非线程安全（需要 ThreadLocal 或对象池管理）。

**Q: Dubbo 如何动态切换序列化方式？**
A: 协议头 Flag 字节的低 3 位存储序列化 ID。发送方设置，接收方读取后通过 SPI 动态加载对应实现。不同接口可以配置不同的序列化方式。

## 六、快速复习入口

| 想了解的内容 | 文件路径 |
|-------------|---------|
| @SPI 序列化接口 | `src/main/java/.../common/serialize/Serialization.java` |
| Hessian2 实现 | `src/main/java/.../common/serialize/hessian2/Hessian2Serialization.java` |
| Fastjson 实现 | `src/main/java/.../common/serialize/fastjson/FastJsonSerialization.java` |
| Kryo 实现 | `src/main/java/.../common/serialize/kryo/KryoSerialization.java` |
| SPI 配置文件 | `src/main/resources/META-INF/dubbo/internal/com.axon.dubbo.common.serialize.Serialization` |
| 升级后的编解码 | `src/main/java/.../remoting/exchange/DubboCodec.java` |
| 单元测试 | `src/test/java/.../demo/ApiTest.java` |
