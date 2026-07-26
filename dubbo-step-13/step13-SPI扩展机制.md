# Step 13：SPI 扩展机制（核心精讲）

## 一、本步骤解决的问题

**核心问题：如何让框架支持"微内核 + 插件化"架构？**

前面 12 步构建的框架中，所有组件（LoadBalance、Cluster、Protocol 等）都是硬编码的。如果要替换实现，必须改代码。Dubbo 通过 SPI（Service Provider Interface）实现了"可插拔"的插件体系，让框架扩展变得极其简单。

```
没有 SPI:                              有了 SPI:
════════                                ══════

new RandomLoadBalance()                 loader.getExtension("random")
  硬编码，换实现要改代码                    从配置文件加载，改名即可切换

if ("failover".equals(...)) {            loader.getExtension(clusterName)
    new FailoverCluster();                一行代码搞定所有策略
} else if ("failfast") { ... }
```

## 二、新增了哪些能力

- ✅ `@SPI` 注解 — 标记扩展点接口
- ✅ `@Adaptive` 注解 — 自适应扩展
- ✅ `@Activate` 注解 — 自动激活
- ✅ `ExtensionLoader<T>` — 扩展点加载器（核心）
- ✅ 按名称获取扩展、缓存、单例
- ✅ Wrapper 自动包装（AOP）
- ✅ IOC setter 注入
- ✅ @Activate 条件激活

## 三、Dubbo SPI vs JDK SPI

| 特性 | JDK SPI | Dubbo SPI |
|------|---------|-----------|
| 加载方式 | 全量加载 | **按需加载（按名称）** |
| 默认值 | 不支持 | **@SPI("default")** |
| AOP | 不支持 | **Wrapper 自动包装** |
| IOC | 不支持 | **set 注入** |
| 自适应 | 不支持 | **@Adaptive** |
| 条件激活 | 不支持 | **@Activate(group, value)** |
| 配置文件 | META-INF/services/ | META-INF/dubbo/internal/ |
| 文件格式 | 类名（一行一个） | **key=类名**（支持命名） |

## 四、核心原理剖析

### 4.1 ExtensionLoader 工作流程

```
getExtension("bmw")
  │
  ├─ 1. 检查缓存 cachedInstances["bmw"]
  │     └→ 有 → 直接返回
  │
  ├─ 2. 加载扩展类 loadExtensionClasses()
  │     ├→ 读取 META-INF/dubbo/internal/<接口名>
  │     ├→ 解析: benz=com.axon.BenzCar
  │     ├→ 检查 @Adaptive → cachedAdaptiveClass
  │     ├→ 检查 Wrapper → cachedWrapperClasses
  │     └→ 其他 → extensionClasses[name] = class
  │
  ├─ 3. 创建实例 createExtension("bmw")
  │     ├→ clazz = extensionClasses.get("bmw")
  │     ├→ constructor.newInstance()
  │     ├→ injectExtension(instance)  ← IOC
  │     └→ wrap with Wrappers         ← AOP
  │
  └─ 4. 放入缓存 → 返回
```

### 4.2 Wrapper 机制（AOP）

```java
// 什么是 Wrapper？
// 有一个构造器，参数为扩展接口类型

public class CarWrapper implements Car {
    private final Car car;          // ← 参数为 Car 类型

    public CarWrapper(Car car) {    // ← 这个构造器让 ExtensionLoader 识别为 Wrapper
        this.car = car;
    }

    @Override
    public String drive() {
        System.out.println("前置处理");       // ← 前置增强
        String result = car.drive();         // ← 调用原始实现
        System.out.println("后置处理");       // ← 后置增强
        return "[Wrapped] " + result;
    }
}

// 所有 Car 实例在创建时都会被 CarWrapper 自动包装
Car benz = loader.getExtension("benz");
// benz 实际是: CarWrapper(BenzCar)
// drive() → 前置 → BenzCar.drive() → 后置
```

### 4.3 IOC 注入

```java
// 自动为 setter 方法注入依赖

public class MyClusterInvoker implements Invoker {
    private LoadBalance loadBalance;  // LoadBalance 也是 @SPI 接口

    // ExtensionLoader 会自动注入 LoadBalance 的默认实现
    public void setLoadBalance(LoadBalance lb) {
        this.loadBalance = lb;
    }
}
```

注入逻辑：
```java
private T injectExtension(T instance) {
    for (Method method : instance.getClass().getMethods()) {
        if (isSetter(method)) {                        // setXxx(单一参数)
            Class<?> type = method.getParameterTypes()[0];
            if (type.isAnnotationPresent(SPI.class)) {  // 参数是 @SPI 接口
                ExtensionLoader<?> loader = getExtensionLoader(type);
                Object adapt = loader.getAdaptiveExtension(); // 注入自适应代理
                method.invoke(instance, adapt);
            }
        }
    }
}
```

### 4.4 @Activate 条件激活

```
@Activate(group = "provider", order = 100)
public class ExceptionFilter implements Filter { ... }

@Activate(group = "provider", order = 200)
public class TimeoutFilter implements Filter { ... }

获取时：
  loader.getActivateExtension("provider")
  → 自动加载所有 group="provider" 的 Filter
  → 按 order 排序: [ExceptionFilter(100), TimeoutFilter(200)]
```

### 4.5 配置文件格式

```
# META-INF/dubbo/internal/com.axon.dubbo.demo.car.Car

benz=com.axon.dubbo.demo.car.BenzCar       # 按名称获取: getExtension("benz")
bmw=com.axon.dubbo.demo.car.BmwCar         # getExtension("bmw")
redflag=com.axon.dubbo.demo.car.RedFlagCar  # getExtension("redflag")
wrapper=com.axon.dubbo.demo.car.CarWrapper  # Wrapper 类（自动包装所有实例）
f1=com.axon.dubbo.demo.car.CarFilter1      # @Activate(group="car") 自动激活
```

### 4.6 @Adaptive 的两种用法

**用法1：标记在类上**
```java
@Adaptive
public class AdaptiveExtensionFactory implements ExtensionFactory {
    // 直接作为自适应实现，ExtensionLoader 直接使用它
}
```

**用法2：标记在方法上**
```java
@SPI("dubbo")
public interface Protocol {
    @Adaptive({"protocol"})            // ← 方法级自适应
    <T> Exporter<T> export(Invoker<T> invoker);
}

// ExtensionLoader 动态生成代理类：
// public class Protocol$Adaptive implements Protocol {
//     public <T> Exporter<T> export(Invoker<T> invoker) {
//         URL url = invoker.getUrl();
//         String extName = url.getParameter("protocol", "dubbo");
//         Protocol protocol = ExtensionLoader
//             .getExtensionLoader(Protocol.class)
//             .getExtension(extName);
//         return protocol.export(invoker);
//     }
// }
```

### 4.7 Holder 的延迟加载模式

```java
static class Holder<T> {
    private volatile T value;    // volatile 保证可见性

    T get() { return value; }
    void set(T value) { this.value = value; }
}

// 双重检查锁定（DCL）模式：
Holder<Object> holder = cachedInstances.get(name);
Object instance = holder.get();                  // ① volatile read（无锁）
if (instance == null) {
    synchronized (holder) {                      // ② 加锁
        instance = holder.get();                 // ③ 再次检查
        if (instance == null) {
            instance = createExtension(name);    // ④ 真正创建
            holder.set(instance);                // ⑤ volatile write
        }
    }
}
return instance;
```

## 五、面试常见问法

**Q: Dubbo 的 SPI 和 JDK 的 SPI 有什么区别？**
A: 四大区别：(1) JDK SPI 全量加载所有实现，Dubbo SPI 按需按名称加载；(2) Dubbo SPI 支持默认值 @SPI("default")；(3) Dubbo SPI 支持 AOP（Wrapper 自动包装）和 IOC（setter 注入）；(4) Dubbo SPI 支持 @Adaptive 自适应代理和 @Activate 条件激活。

**Q: Dubbo 的 Wrapper 机制是什么？AOP 如何实现？**
A: ExtensionLoader 检查实现类的构造器，如果构造器参数是扩展接口类型，则识别为 Wrapper。获取扩展时，所有 Wrapper 会自动包装原始实例，形成调用链。这是典型的装饰器模式实现 AOP。

**Q: @Adaptive 注解有什么用？**
A: @Adaptive 实现"自适应扩展"——不需要指定扩展名，框架根据 URL 参数自动选择。例如 Protocol 接口的 @Adaptive({"protocol"}) 使得运行时能从 URL 的 protocol 参数（如 "dubbo"、"http"）动态选择对应的 Protocol 实现。

**Q: Dubbo 为什么不用 Spring 的依赖注入而自己实现 SPI？**
A: Dubbo 需要运行时动态发现和加载扩展，而不是编译时就确定。Dubbo SPI 的 @Adaptive 动态代理、Wrapper 链、@Activate 条件激活等特性，Spring 的 DI 容器无法直接提供。

## 六、快速复习入口

| 想了解的内容 | 文件路径 |
|-------------|---------|
| @SPI 注解 | `src/main/java/.../common/extension/SPI.java` |
| @Adaptive 注解 | `src/main/java/.../common/extension/Adaptive.java` |
| @Activate 注解 | `src/main/java/.../common/extension/Activate.java` |
| ExtensionLoader | `src/main/java/.../common/extension/ExtensionLoader.java` |
| Car SPI 演示接口 | `src/test/java/.../demo/car/Car.java` |
| CarWrapper (AOP) | `src/test/java/.../demo/car/CarWrapper.java` |
| SPI 配置文件 | `src/test/resources/META-INF/dubbo/internal/com.axon.dubbo.demo.car.Car` |
| 单元测试 | `src/test/java/.../demo/ApiTest.java` |
