package com.axon.dubbo.config;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.registry.RegistryService;
import com.axon.dubbo.rpc.*;
import com.axon.dubbo.rpc.protocol.DubboProtocol;
import com.axon.dubbo.rpc.protocol.Protocol;
import com.axon.dubbo.rpc.proxy.ProxyFactory;
import com.axon.dubbo.rpc.proxy.jdk.JdkProxyFactory;

/**
 * 服务消费者配置
 *
 * 封装 Consumer 端的完整启动流程：
 * 注册中心 → 协议 → refer → Proxy
 *
 * 使用示例：
 * ReferenceConfig<IUserService> config = new ReferenceConfig<>();
 * config.setInterface(IUserService.class);
 * config.setRegistry(new RegistryConfig("local", 0));
 * IUserService service = config.get();  // 一行获取代理！
 *
 * @param <T> 服务接口类型
 * @author axon-dubbo
 * @since 1.0.0
 */
public class ReferenceConfig<T> {

    private Class<T> interfaceClass;
    private RegistryConfig registry = new RegistryConfig();
    private String version = "1.0.0";

    private Protocol dubboProtocol;
    private RegistryService registryService;
    private T proxy;

    public ReferenceConfig() {}

    // ==================== Setters ====================

    public void setInterface(Class<T> c) { interfaceClass = c; }
    public void setRegistry(RegistryConfig r) { registry = r; }
    public void setVersion(String v) { version = v; }

    // ==================== get ====================

    /**
     * 获取服务代理（一键引用）
     */
    public synchronized T get() {
        if (proxy != null) return proxy; // 已引用

        if (interfaceClass == null) throw new IllegalStateException("interface 不能为空");

        // 1. 创建注册中心
        registryService = createRegistry();

        // 2. 创建协议
        dubboProtocol = new DubboProtocol(registryService);

        // 3. 构建 URL
        URL serviceUrl = URL.builder()
                .path(interfaceClass.getName())
                .addParameter("version", version).build();

        // 4. refer → Proxy
        ProxyFactory proxyFactory = new JdkProxyFactory();
        Invoker<T> invoker = dubboProtocol.refer(interfaceClass, serviceUrl);
        proxy = proxyFactory.getProxy(invoker);

        System.out.println("[ReferenceConfig] 服务已引用: " + interfaceClass.getSimpleName());
        return proxy;
    }

    private RegistryService createRegistry() {
        return RegistryConfig.createRegistry(registry);
    }
}
