# Step 02：Java 序列化传输

## 一、本步骤解决的问题

**核心问题：Java 对象如何在网络上传输？**

Step 01 只能传输字符串，但真实的 RPC 调用需要传递复杂的 Java 对象：方法参数可能是 `User`、`Order` 等 POJO，返回值也可能是任意类型。本步骤引入序列化机制，实现 **"Java 对象 → 字节 → 网络 → 字节 → Java 对象"** 的完整转换链路。

## 二、新增了哪些能力

- ✅ `Serialization` 序列化接口抽象
- ✅ JDK 原生序列化实现（`JavaSerialization`）
- ✅ `Request` / `Response` 支持 `Serializable`，可携带完整 RPC 调用信息
- ✅ 服务端读取字节数组 → 反序列化 → 处理 → 序列化 → 返回字节数组
- ✅ 客户端构造 Request → 序列化 → 发送字节 → 接收字节 → 反序列化为 Response
- ✅ 引入了简易的"长度前缀"协议（解决 TCP 粘包/拆包问题）

## 三、核心类一览

| 类名 | 作用 | Dubbo 中对应概念 |
|------|------|-----------------|
| `Serialization` | 序列化接口抽象 | `org.apache.dubbo.common.serialize.Serialization` |
| `JavaSerialization` | JDK 原生序列化实现 | —（Dubbo 默认用 Hessian2） |
| `Request` (升级) | 携带接口名/方法名/参数类型/参数值，支持序列化 | `org.apache.dubbo.remoting.exchange.Request` |
| `Response` (升级) | 携带返回值或异常信息，支持序列化 | `org.apache.dubbo.remoting.exchange.Response` |
| `ObjectServer` | 服务端：字节数组收发 + 序列化/反序列化 | Transport 层 + Serialization 层 |
| `ObjectClient` | 客户端：字节数组收发 + 序列化/反序列化 | Transport 层 + Serialization 层 |

## 四、核心原理剖析

### 4.1 序列化的本质

```
JVM 堆内存中的对象                    网络/磁盘上的字节
══════════════════════              ═══════════════════
User {                               01001100 11010011
  name: "张三"        序列化 →       10100110 00111001
  age: 25             ← 反序列化     11100010 01010001
  email: "z@a.com"                  00110100 10101110
}                                   ...（二进制数据）
```

**为什么需要序列化？**
- Java 对象存在于 JVM 堆内存中，有特定的内存布局（对象头、实例数据、对齐填充）
- 网络只能传输字节流（byte stream）
- 序列化 = 将对象"拍平"为线性字节序列
- 反序列化 = 将字节序列"重建"为对象

### 4.2 JDK 序列化流程

```java
// 序列化：对象 → 字节
ByteArrayOutputStream baos = new ByteArrayOutputStream();
ObjectOutputStream oos = new ObjectOutputStream(baos);
oos.writeObject(obj);        // ← 魔法在这里发生
byte[] bytes = baos.toByteArray();

// writeObject() 内部做了什么？
// 1. 写入魔数 0xACED (STREAM_MAGIC)
// 2. 写入版本号 0x0005 (STREAM_VERSION)
// 3. 写入类描述信息（类名、serialVersionUID、字段数量、字段名、字段类型）
// 4. 递归写入所有字段的值（基本类型直接写，引用类型递归序列化）
// 5. 处理引用共享（同一个对象不会重复写入，用引用 ID 代替）

// 反序列化：字节 → 对象
ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
ObjectInputStream ois = new ObjectInputStream(bais);
Object obj = ois.readObject();

// readObject() 内部做了什么？
// 1. 读取并校验魔数和版本号
// 2. 读取类描述信息
// 3. 通过反射调用无参构造器创建对象实例
// 4. 递归读取字段值并通过反射 set 到对象上
```

### 4.3 简易帧协议设计

TCP 是"流式"协议，数据像水流一样，没有自然的边界。发送方连续发送两段数据，接收方可能一次收到全部，也可能分多次收到。这就是著名的**粘包/拆包问题**。

```
发送方连续发送：
  [AAAA] [BBBBBB]

接收方可能收到：
  情况1（粘包）: [AAAABBBBBB]    ← 一次收到全部
  情况2（拆包）: [AA] [AABBB] [BBB] ← 分多次收到
```

**解决方案：长度前缀帧协议**

```
┌─────────────────┬────────────────────────────┐
│   4 bytes (int) │       N bytes               │
│   数据体长度     │       序列化后的数据体       │
└─────────────────┴────────────────────────────┘
```

```java
// 发送方：先写长度，再写数据
dos.writeInt(data.length);   // 4 字节长度前缀
dos.write(data);              // N 字节数据体

// 接收方：先读长度，再读数据
int length = dis.readInt();   // 知道要读取多少字节
byte[] data = new byte[length];
dis.readFully(data);           // 精确读取 N 字节
```

> 💡 Dubbo 协议头的前 16 字节中，第 12-15 字节就是数据体长度，用的正是同样的"长度前缀"思想。

### 4.4 完整通信链路

```
客户端                                        服务端
──────                                        ──────

Request{id=1, interface="UserService",
        method="getUser", args=[1001L]}
    │
    │ serialization.serialize(request)
    ▼
[0xACED0005... 278 bytes]
    │
    │ dos.writeInt(278)   ← 长度前缀
    │ dos.write(bytes)    ← 数据体
    ▼
═══════════════════ 网络传输 ═══════════════════
                                                │
                                                │ dis.readInt() = 278
                                                │ dis.readFully(bytes)
                                                ▼
                                          [0xACED0005... 278 bytes]
                                                │
                                                │ serialization.deserialize(bytes, Request.class)
                                                ▼
                                          Request{id=1, ...}
                                                │
                                                │ processRequest()
                                                ▼
                                          Response{success=true, result="..."}
                                                │
                                                │ serialization.serialize(response)
                                                ▼
                                          [0xACED0005... 156 bytes]
                                                │
                                                │ dos.writeInt(156)
                                                │ dos.write(bytes)
                                                ▼
═══════════════════ 网络传输 ═══════════════════
    │
    │ dis.readInt() = 156
    │ dis.readFully(bytes)
    ▼
[0xACED0005... 156 bytes]
    │
    │ serialization.deserialize(bytes, Response.class)
    ▼
Response{success=true, result="Hello 1001! 来自服务端的问候。"}
```

### 4.5 JDK 序列化的序列化结果分析

```
序列化 Request{id=1, interfaceName="com.axon.dubbo.demo.UserService"} 的结果：

HEX 内容（开头部分）:
AC ED 00 05    ← 魔数 0xACED + 版本号 0x0005（JDK 序列化标准头）
75 72 00 1F    ← TC_ARRAY + 类名长度
...

可以看到，JDK 序列化携带了大量的元数据（类全名、字段名等），
这导致序列化后的字节数远大于原始数据大小。
```

## 五、面试常见问法

**Q: 为什么要序列化？直接传对象不行吗？**
A: Java 对象存在于 JVM 堆内存中，其内存布局是 JVM 特定的，不同 JVM 实现可能不同。而且对象的引用（指针）在另一个 JVM 中没有意义。必须将对象转换为标准的、平台无关的字节序列才能在网络上传输。

**Q: JDK 序列化有什么缺点？Dubbo 为什么不用它？**
A: 三个主要缺点：
1. **体积大**：携带了类的完整描述信息（类名、字段名等），字节数远超实际数据
2. **性能差**：大量使用反射，序列化/反序列化速度慢
3. **跨语言差**：只能 Java 使用，不符合 Dubbo 的多语言支持定位
Dubbo 默认使用 Hessian2，体积小、速度快、跨语言好。

**Q: serialVersionUID 是做什么的？不写会怎样？**
A: `serialVersionUID` 是序列化版本号，用于验证序列化发送方和接收方的类是否兼容。不写的话 JVM 会根据类结构（字段、方法等）自动生成一个 hash 值，类结构一旦变化（哪怕只是加个空格），版本号就不同，导致反序列化失败。

**Q: 什么是粘包/拆包？如何解决？**
A: TCP 是流式协议，数据没有边界。发送方发两段数据，接收方可能一次收到（粘包），也可能分多次收到（拆包）。解决方案有：固定长度、分隔符、长度前缀。Dubbo 采用长度前缀（在协议头中携带数据体长度）。

## 六、快速复习入口

| 想了解的内容 | 文件路径 |
|-------------|---------|
| 序列化接口 | `src/main/java/.../common/serialize/Serialization.java` |
| JDK 序列化实现 | `src/main/java/.../common/serialize/java/JavaSerialization.java` |
| 升级后的请求对象 | `src/main/java/.../transport/socket/Request.java` |
| 升级后的响应对象 | `src/main/java/.../transport/socket/Response.java` |
| 序列化服务端 | `src/main/java/.../transport/socket/ObjectServer.java` |
| 序列化客户端 | `src/main/java/.../transport/socket/ObjectClient.java` |
| 单元测试 | `src/test/java/.../transport/socket/ApiTest.java` |
