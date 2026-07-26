package com.axon.dubbo.common.extension;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 扩展点加载器 —— Dubbo SPI 核心
 *
 * 功能：
 * 1. 从配置文件中加载扩展实现类
 * 2. 按名称获取扩展实例（缓存）
 * 3. 支持 @Adaptive 自适应代理
 * 4. 支持 Wrapper 类自动包装（AOP）
 * 5. 支持 IOC 注入（setXXX 方法自动注入依赖）
 * 6. 支持 @Activate 自动激活
 *
 * 配置文件位置：META-INF/dubbo/internal/<接口全限定名>
 * 配置文件格式：key=value（如：random=com.xxx.RandomLoadBalance）
 *
 * 对应官方源码：org.apache.dubbo.common.extension.ExtensionLoader
 *
 * @param <T> 扩展点接口类型
 * @author axon-dubbo
 * @since 1.0.0
 */
public class ExtensionLoader<T> {

    /** SPI 配置文件目录 */
    private static final String SERVICES_DIRECTORY = "META-INF/dubbo/internal/";

    /** 全局缓存：扩展点接口 → ExtensionLoader 实例 */
    private static final ConcurrentMap<Class<?>, ExtensionLoader<?>> LOADERS = new ConcurrentHashMap<>();

    /** 当前扩展点接口类型 */
    private final Class<T> type;

    /** 扩展点接口上的 @SPI 注解 */
    private final SPI spiAnnotation;

    /** 缓存：扩展名 → 实例持有者 */
    private final ConcurrentMap<String, Holder<Object>> cachedInstances = new ConcurrentHashMap<>();

    /** 缓存：扩展名 → 实现类 */
    private final Holder<Map<String, Class<?>>> cachedClasses = new Holder<>();

    /** 缓存：Wrapper 类集合（有一个参数的构造器，参数类型为扩展接口） */
    private Set<Class<?>> cachedWrapperClasses;

    /** 缓存：@Adaptive 标记的类 */
    private volatile Class<?> cachedAdaptiveClass;

    /** 缓存：自适应扩展实例 */
    private final Holder<Object> cachedAdaptiveInstance = new Holder<>();

    private ExtensionLoader(Class<T> type) {
        this.type = type;
        this.spiAnnotation = type.getAnnotation(SPI.class);
    }

    // ==================== 获取 ExtensionLoader ====================

    @SuppressWarnings("unchecked")
    public static <T> ExtensionLoader<T> getExtensionLoader(Class<T> type) {
        if (type == null) throw new IllegalArgumentException("type == null");
        if (!type.isInterface()) throw new IllegalArgumentException("type must be interface: " + type);
        if (!type.isAnnotationPresent(SPI.class))
            throw new IllegalArgumentException("type must have @SPI annotation: " + type);

        ExtensionLoader<T> loader = (ExtensionLoader<T>) LOADERS.get(type);
        if (loader == null) {
            LOADERS.putIfAbsent(type, new ExtensionLoader<>(type));
            loader = (ExtensionLoader<T>) LOADERS.get(type);
        }
        return loader;
    }

    // ==================== 获取扩展实例 ====================

    /**
     * 获取指定名称的扩展实例
     */
    public T getExtension(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Extension name must not be empty");
        }

        Holder<Object> holder = cachedInstances.get(name);
        if (holder == null) {
            cachedInstances.putIfAbsent(name, new Holder<>());
            holder = cachedInstances.get(name);
        }

        Object instance = holder.get();
        if (instance == null) {
            synchronized (holder) {
                instance = holder.get();
                if (instance == null) {
                    instance = createExtension(name);
                    holder.set(instance);
                }
            }
        }
        return (T) instance;
    }

    /**
     * 获取默认扩展实例（@SPI 注解的 value）
     */
    public T getDefaultExtension() {
        if (spiAnnotation == null || spiAnnotation.value().isEmpty()) {
            throw new IllegalStateException("@SPI 未指定默认值: " + type.getName());
        }
        return getExtension(spiAnnotation.value());
    }

    /**
     * 获取自适应扩展实例
     */
    @SuppressWarnings("unchecked")
    public T getAdaptiveExtension() {
        Object instance = cachedAdaptiveInstance.get();
        if (instance == null) {
            synchronized (cachedAdaptiveInstance) {
                instance = cachedAdaptiveInstance.get();
                if (instance == null) {
                    instance = createAdaptiveExtension();
                    cachedAdaptiveInstance.set(instance);
                }
            }
        }
        return (T) instance;
    }

    /**
     * 获取所有支持的扩展名
     */
    public Set<String> getSupportedExtensions() {
        return new TreeSet<>(getExtensionClasses().keySet());
    }

    // ==================== 获取自动激活的扩展（@Activate） ====================

    /**
     * 获取符合条件的 @Activate 扩展列表
     */
    public List<T> getActivateExtension(String group) {
        Map<String, Class<?>> classes = getExtensionClasses();
        List<T> result = new ArrayList<>();

        for (Map.Entry<String, Class<?>> entry : classes.entrySet()) {
            Class<?> clazz = entry.getValue();
            Activate activate = clazz.getAnnotation(Activate.class);
            if (activate != null) {
                for (String g : activate.group()) {
                    if (g.equals(group)) {
                        result.add(getExtension(entry.getKey()));
                        break;
                    }
                }
            }
        }

        // 按 @Activate.order 排序
        result.sort((a, b) -> {
            Activate aa = a.getClass().getAnnotation(Activate.class);
            Activate bb = b.getClass().getAnnotation(Activate.class);
            return Integer.compare(aa != null ? aa.order() : 0, bb != null ? bb.order() : 0);
        });

        return result;
    }

    // ==================== 内部实现 ====================

    /**
     * 创建扩展实例
     */
    @SuppressWarnings("unchecked")
    private T createExtension(String name) {
        Class<?> clazz = getExtensionClasses().get(name);
        if (clazz == null) {
            throw new IllegalStateException("No extension named '" + name
                    + "' found for " + type.getName());
        }

        try {
            // 1. 创建实例
            Constructor<?> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            T instance = (T) constructor.newInstance();

            // 2. IOC 注入（setXXX 方法自动装配依赖）
            injectExtension(instance);

            // 3. Wrapper 包装（AOP）
            Set<Class<?>> wrapperClasses = getWrapperClasses();
            if (!wrapperClasses.isEmpty()) {
                for (Class<?> wrapperClass : wrapperClasses) {
                    Constructor<?> wrapperCtor = wrapperClass.getConstructor(type);
                    instance = (T) wrapperCtor.newInstance(instance);
                    injectExtension(instance);
                }
            }

            return instance;

        } catch (Exception e) {
            throw new RuntimeException("创建扩展实例失败: " + clazz.getName(), e);
        }
    }

    /**
     * 创建自适应扩展
     */
    @SuppressWarnings("unchecked")
    private T createAdaptiveExtension() {
        try {
            // 1. 如果某个实现类标记了 @Adaptive，直接使用它
            getExtensionClasses(); // 触发类加载
            if (cachedAdaptiveClass != null) {
                Constructor<?> ctor = cachedAdaptiveClass.getDeclaredConstructor();
                ctor.setAccessible(true);
                return (T) ctor.newInstance();
            }

            // 2. 检查接口方法上是否有 @Adaptive
            for (Method method : type.getMethods()) {
                if (method.isAnnotationPresent(Adaptive.class)) {
                    // 有 @Adaptive 方法 → 需要通过动态代理生成自适应实现
                    // 这里只提示，完整实现需要字节码生成（Javassist）
                    System.out.println("[ExtensionLoader] 接口 " + type.getSimpleName()
                            + " 的方法 " + method.getName()
                            + "() 标记了 @Adaptive，需要动态代理");
                }
            }

            throw new IllegalStateException("没有找到 @Adaptive 实现或本版本不支持方法级 @Adaptive 动态代理");

        } catch (Exception e) {
            throw new RuntimeException("创建自适应扩展失败", e);
        }
    }

    // ==================== IOC 注入 ====================

    /**
     * 对实例进行 IOC 注入
     * 遍历所有 public setter 方法，如果参数类型有对应的 ExtensionLoader，自动注入
     */
    private T injectExtension(T instance) {
        try {
            for (Method method : instance.getClass().getMethods()) {
                if (isSetter(method)) {
                    Class<?> paramType = method.getParameterTypes()[0];
                    // 尝试为 setter 参数注入依赖
                    if (paramType.isInterface() && paramType.isAnnotationPresent(SPI.class)) {
                        try {
                            ExtensionLoader<?> loader = getExtensionLoader(paramType);
                            Object adapt = loader.getAdaptiveExtension();
                            method.invoke(instance, adapt);
                        } catch (Exception ignored) {
                            // 注入失败不影响主流程
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[ExtensionLoader] IOC 注入异常: " + e.getMessage());
        }
        return instance;
    }

    private boolean isSetter(Method method) {
        return method.getName().startsWith("set")
                && method.getParameterTypes().length == 1
                && Modifier.isPublic(method.getModifiers())
                && !Modifier.isStatic(method.getModifiers());
    }

    // ==================== 类加载 ====================

    /**
     * 加载所有扩展实现类
     */
    private Map<String, Class<?>> getExtensionClasses() {
        Map<String, Class<?>> classes = cachedClasses.get();
        if (classes == null) {
            synchronized (cachedClasses) {
                classes = cachedClasses.get();
                if (classes == null) {
                    classes = loadExtensionClasses();
                    cachedClasses.set(classes);
                }
            }
        }
        return classes;
    }

    /**
     * 从配置文件中加载扩展实现类
     */
    private Map<String, Class<?>> loadExtensionClasses() {
        Map<String, Class<?>> extensionClasses = new HashMap<>();
        loadDirectory(extensionClasses, SERVICES_DIRECTORY);
        return extensionClasses;
    }

    private void loadDirectory(Map<String, Class<?>> extensionClasses, String dir) {
        String fileName = dir + type.getName();
        try {
            Enumeration<URL> urls;
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            if (classLoader != null) {
                urls = classLoader.getResources(fileName);
            } else {
                urls = ClassLoader.getSystemResources(fileName);
            }

            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // 跳过注释和空行
                        int ci = line.indexOf('#');
                        if (ci >= 0) line = line.substring(0, ci);
                        line = line.trim();
                        if (line.isEmpty()) continue;

                        // 解析 key=value
                        int ei = line.indexOf('=');
                        String name = ei > 0 ? line.substring(0, ei).trim() : line;
                        String className = ei > 0 ? line.substring(ei + 1).trim() : line;

                        try {
                            Class<?> clazz = Class.forName(className, true, classLoader);
                            if (!type.isAssignableFrom(clazz)) {
                                System.err.println("[ExtensionLoader] " + clazz.getName()
                                        + " 不是 " + type.getName() + " 的子类型");
                                continue;
                            }

                            // 检查 @Adaptive（类级别）
                            if (clazz.isAnnotationPresent(Adaptive.class)) {
                                cachedAdaptiveClass = clazz;
                            }
                            // 检查是否是 Wrapper
                            else if (isWrapperClass(clazz)) {
                                addWrapperClass(clazz);
                            } else {
                                extensionClasses.put(name, clazz);
                            }

                        } catch (ClassNotFoundException e) {
                            System.err.println("[ExtensionLoader] 类未找到: " + className);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[ExtensionLoader] 加载配置异常: " + e.getMessage());
        }
    }

    /**
     * 判断是否为 Wrapper 类
     * Wrapper 类有一个参数为扩展接口类型的构造器
     */
    private boolean isWrapperClass(Class<?> clazz) {
        try {
            clazz.getConstructor(type);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private void addWrapperClass(Class<?> clazz) {
        if (cachedWrapperClasses == null) {
            cachedWrapperClasses = new HashSet<>();
        }
        cachedWrapperClasses.add(clazz);
    }

    private Set<Class<?>> getWrapperClasses() {
        getExtensionClasses(); // 确保已加载
        return cachedWrapperClasses != null ? cachedWrapperClasses : Collections.emptySet();
    }

    // ==================== 内部类 ====================

    /**
     * 延迟初始化持有者
     */
    static class Holder<T> {
        private volatile T value;
        T get() { return value; }
        void set(T value) { this.value = value; }
    }
}
