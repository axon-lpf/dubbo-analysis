package com.axon.dubbo.config;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.registry.RegistryService;
import com.axon.dubbo.rpc.*;
import com.axon.dubbo.rpc.protocol.DubboProtocol;
import com.axon.dubbo.rpc.protocol.Protocol;
import com.axon.dubbo.rpc.proxy.ProxyFactory;
import com.axon.dubbo.rpc.proxy.jdk.JdkProxyFactory;

/**
 * 服务提供者配置
 *
 * 封装 Provider 端的完整启动流程：
 * 注册中心 → 协议 → Invoker → export
 *
 * 使用示例：
 * ServiceConfig<IUserService> config = new ServiceConfig<>();
 * config.setInterface(IUserService.class);
 * config.setRef(new UserServiceImpl());
 * config.setRegistry(new RegistryConfig("local", 0));
 * config.export();  // 一键启动！
 *
 * @param <T> 服务接口类型
 * @author axon-dubbo
 * @since 1.0.0
 */
public class ServiceConfig<T> {

    private Class<T> interfaceClass;
    private T ref;                      // 服务实现类
    private RegistryConfig registry = new RegistryConfig();
    private ProtocolConfig protocol = new ProtocolConfig();
    private String version = "1.0.0";

    private Protocol dubboProtocol;
    private RegistryService registryService;
    private Exporter<T> exporter;

    public ServiceConfig() {}

    // ==================== Setters ====================

    public void setInterface(Class<T> c) { interfaceClass = c; }
    public void setRef(T r) { ref = r; }
    public void setRegistry(RegistryConfig r) { registry = r; }
    public void setProtocol(ProtocolConfig p) { protocol = p; }
    public void setVersion(String v) { version = v; }

    // ==================== export ====================

    /**
     * 导出服务（一键启动）
     */
    public synchronized void export() {
        if (exporter != null) return; // 已导出

        if (interfaceClass == null) throw new IllegalStateException("interface 不能为空");
        if (ref == null) throw new IllegalStateException("ref 不能为空");

        // 1. 创建注册中心
        registryService = createRegistry();

        // 2. 创建协议
        dubboProtocol = new DubboProtocol(registryService);

        // 3. 构建 URL
        URL serviceUrl = URL.builder()
                .protocol("dubbo").host(protocol.getHost()).port(protocol.getPort())
                .path(interfaceClass.getName())
                .addParameter("version", version).build();

        // 4. 导出
        ProxyFactory proxyFactory = new JdkProxyFactory();
        Invoker<T> invoker = proxyFactory.getInvoker(ref, interfaceClass, serviceUrl);
        exporter = dubboProtocol.export(invoker);

        System.out.println("[ServiceConfig] 服务已导出: " + interfaceClass.getSimpleName()
                + " | " + serviceUrl);
    }

    public void unexport() {
        if (exporter != null) {
            exporter.unexport();
            if (dubboProtocol instanceof DubboProtocol) {
                ((DubboProtocol) dubboProtocol).destroy();
            }
            exporter = null;
        }
    }

    private RegistryService createRegistry() {
        return RegistryConfig.createRegistry(registry);
    }
}
