# Step 14：过滤器链（Filter Chain）

## 一、本步骤解决的问题

**核心问题：如何在 RPC 调用的各个环节插入自定义逻辑？**

日志记录、性能监控、异常处理、权限校验、限流——这些横切关注点应该如何优雅地插入到调用链路中？Filter 链（责任链模式）提供了完美的解决方案。

## 二、新增了哪些能力

- ✅ `Filter` — @SPI 过滤器接口
- ✅ `AccessLogFilter` — 访问日志（Provider 端自动激活）
- ✅ `ExceptionFilter` — 异常包装（Provider 端自动激活）
- ✅ `TimeCostFilter` — 耗时统计（Consumer 端自动激活）
- ✅ `DubboProtocol` 升级 — export/refer 自动构建 Filter 链

## 三、核心类一览

| 类名 | 作用 | Dubbo 对应 |
|------|------|-----------|
| `Filter` (@SPI) | 过滤器接口 | `org.apache.dubbo.rpc.Filter` |
| `AccessLogFilter` | 访问日志（@Activate provider） | `org.apache.dubbo.rpc.filter.AccessLogFilter` |
| `ExceptionFilter` | 异常包装（@Activate provider） | `org.apache.dubbo.rpc.filter.ExceptionFilter` |
| `TimeCostFilter` | 耗时统计（@Activate consumer） | — |

## 四、核心原理剖析

### 4.1 Filter 链的责任链模式

```
Consumer 端                              Provider 端
═══════════                              ═══════════

Proxy.invoke()                           Network Request
    │                                          │
    ▼                                          ▼
[TimeCostFilter]                         [AccessLogFilter]
    │ 记录开始时间                              │ 打印调用日志
    │                                          │
    ▼                                          ▼
ClusterInvoker                           [ExceptionFilter]
    │                                          │ 捕获异常
    │                                          │
    ▼                                          ▼
LoadBalance → DubboInvoker               AbstractProxyInvoker
    │                                          │
    ▼                                          ▼
    ═══════════ 网络传输 ═══════════→       Method.invoke()
```

### 4.2 Filter 链的构建方式

```java
// 从后向前构建链表
Invoker<T> last = originalInvoker;  // 最终执行的 Invoker
for (int i = filters.size() - 1; i >= 0; i--) {
    Filter filter = filters.get(i);
    Invoker<T> next = last;
    last = new AbstractInvoker<T>(...) {
        Result doInvoke(Invocation inv) {
            return filter.invoke(next, inv);  // filter 内部调用 next
        }
    };
}
return last;  // 返回最外层的 Filter Invoker
```

构建后调用顺序：`Filter1.doInvoke() → Filter2.doInvoke() → ... → originalInvoker.invoke()`

### 4.3 @Activate 自动激活

```java
@Activate(group = "provider", order = 100)
public class AccessLogFilter implements Filter { ... }

@Activate(group = "provider", order = 200)  
public class ExceptionFilter implements Filter { ... }

// DubboProtocol.export():
List<Filter> filters = ExtensionLoader.getExtensionLoader(Filter.class)
        .getActivateExtension("provider");
// → [AccessLogFilter(100), ExceptionFilter(200)]
// → 按 order 排序后构建链
```

### 4.4 调用时序（完整调用链）

```
Client.main()
  → Proxy.getUser(1001L)
    → InvokerInvocationHandler.invoke()
      → [TimeCostFilter] Consumer Filter (Step 14)
        → FailoverClusterInvoker.invoke()
          → Directory.list() → LoadBalance.select()
            → DubboInvoker.invoke()
              ═══════ TCP ═══════
                → AccessLogFilter (Step 14) → 记录日志
                  → ExceptionFilter (Step 14) → 捕获异常
                    → AbstractProxyInvoker.invoke()
                      → UserServiceImpl.getUser(1001L)
                      ← User{name='User_1001'}
                    ← Result
                  ← Result
                ← Result (日志: 耗时XXms)
              ═══════ TCP ═══════
            ← Result
          ← Result
        ← Result
      ← Result (耗时: XXms)
    ← User
```

### 4.5 SPI 配置文件

```properties
# META-INF/dubbo/internal/com.axon.dubbo.rpc.Filter
accesslog=com.axon.dubbo.rpc.filter.AccessLogFilter
exception=com.axon.dubbo.rpc.filter.ExceptionFilter
timecost=com.axon.dubbo.rpc.filter.TimeCostFilter
```

## 五、面试常见问法

**Q: Dubbo 的 Filter 机制如何工作？**
A: Dubbo 的 Filter 采用责任链模式。每个 Filter 包装下一个 Invoker，形成调用链。Provider 端和 Consumer 端各有独立的 Filter 链。通过 @SPI + @Activate 注解实现自动发现和加载，按 order 排序。

**Q: 如何在 Dubbo 中实现自定义 Filter？**
A: 实现 `Filter` 接口，添加 `@Activate(group = {"provider"})` 注解（指定 provider/consumer），在 META-INF/dubbo/ 下配置 SPI 文件。框架会自动加载并按 order 排序插入调用链。

**Q: Filter 链和 Spring MVC 的 Interceptor 有什么异同？**
A: 都是责任链模式实现 AOP，但 Filter 作用于 RPC 调用层（通信前后），Interceptor 作用于 Web 请求层（Controller 前后）。Dubbo Filter 可以通过 SPI 动态加载，Spring Interceptor 通过注册配置。

## 六、快速复习入口

| 想了解的内容 | 文件路径 |
|-------------|---------|
| Filter 接口 (@SPI) | `src/main/java/.../rpc/Filter.java` |
| AccessLogFilter | `src/main/java/.../rpc/filter/AccessLogFilter.java` |
| ExceptionFilter | `src/main/java/.../rpc/filter/ExceptionFilter.java` |
| TimeCostFilter | `src/main/java/.../rpc/filter/TimeCostFilter.java` |
| Filter SPI 配置 | `src/main/resources/META-INF/dubbo/internal/com.axon.dubbo.rpc.Filter` |
| 集成 Filter 的协议 | `src/main/java/.../rpc/protocol/DubboProtocol.java` |
| 单元测试 | `src/test/java/.../demo/ApiTest.java` |
