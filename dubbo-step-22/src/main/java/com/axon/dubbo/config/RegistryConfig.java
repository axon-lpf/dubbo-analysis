package com.axon.dubbo.config;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.registry.LocalRegistry;
import com.axon.dubbo.registry.RegistryService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 注册中心配置
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class RegistryConfig {
    private String protocol = "local";
    private String address = "127.0.0.1";
    private int port = 0;

    /**
     * 全局共享的注册中心实例（按地址缓存）
     * 确保 ServiceConfig 和 ReferenceConfig 使用同一个注册中心
     */
    private static final Map<String, RegistryService> REGISTRIES = new ConcurrentHashMap<>();

    public RegistryConfig() {}
    public RegistryConfig(String protocol, int port) {
        this.protocol = protocol; this.port = port;
    }

    public String getProtocol() { return protocol; }
    public void setProtocol(String p) { protocol = p; }
    public String getAddress() { return address; }
    public void setAddress(String a) { address = a; }
    public int getPort() { return port; }
    public void setPort(int p) { port = p; }

    @Override
    public String toString() { return protocol + "://" + address + ":" + port; }

    /**
     * 获取或创建注册中心实例（全局共享）
     */
    public static RegistryService createRegistry(RegistryConfig config) {
        String key = config.getProtocol() + "://" + config.getAddress();
        return REGISTRIES.computeIfAbsent(key, k -> {
            if ("local".equals(config.getProtocol())) {
                return new LocalRegistry(URL.builder()
                        .protocol("registry").host(config.getAddress())
                        .port(config.getPort()).path("local").build());
            }
            throw new UnsupportedOperationException("暂不支持: " + config.getProtocol());
        });
    }
}
