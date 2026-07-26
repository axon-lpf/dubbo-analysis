package com.axon.dubbo.common;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * URL —— Dubbo 核心数据模型
 *
 * 在 Dubbo 中，URL 不仅仅是网络地址，而是一个贯穿整个框架的"配置总线"。
 * 所有配置信息（注册中心地址、服务元数据、协议参数、集群策略等）都以 URL 形式传递。
 *
 * URL 格式（参考 Dubbo）：
 * protocol://host:port/path?key1=value1&key2=value2
 *
 * 例如：
 * dubbo://192.168.1.100:20880/com.axon.demo.IUserService?version=1.0.0&timeout=3000
 *
 * 为什么用 URL 而不是 Map？
 * 1. URL 是一个不可变的、可读的、可序列化的标准结构
 * 2. 所有 Dubbo 组件通过 URL 获取配置，实现组件间的松耦合
 * 3. URL 的 toString() 可以直接作为注册中心中服务的唯一标识
 *
 * 对应官方源码：org.apache.dubbo.common.URL
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class URL implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 协议名称（如 dubbo、http、registry）
     */
    private final String protocol;

    /**
     * 主机地址
     */
    private final String host;

    /**
     * 端口号
     */
    private final int port;

    /**
     * 路径（通常是接口全限定名，如 com.axon.demo.IUserService）
     */
    private final String path;

    /**
     * 扩展参数（版本号、超时时间、权重等）
     */
    private final Map<String, String> parameters;

    /**
     * 完整 URL 字符串缓存（toString 结果）
     */
    private transient String string;

    public URL(String protocol, String host, int port, String path) {
        this(protocol, host, port, path, new HashMap<>());
    }

    public URL(String protocol, String host, int port, String path, Map<String, String> parameters) {
        this.protocol = protocol;
        this.host = host;
        this.port = port;
        this.path = path;
        this.parameters = Collections.unmodifiableMap(new HashMap<>(parameters));
    }

    // ==================== 参数访问 ====================

    /**
     * 获取参数值
     */
    public String getParameter(String key) {
        return parameters.get(key);
    }

    /**
     * 获取参数值，不存在时返回默认值
     */
    public String getParameter(String key, String defaultValue) {
        String value = parameters.get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取 int 类型参数
     */
    public int getParameter(String key, int defaultValue) {
        String value = parameters.get(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    // ==================== URL 构建器（流式 API） ====================

    /**
     * 创建一个新的 URL，添加一个参数
     */
    public URL addParameter(String key, String value) {
        if (key == null || value == null) {
            return this;
        }
        Map<String, String> newParams = new HashMap<>(this.parameters);
        newParams.put(key, value);
        return new URL(protocol, host, port, path, newParams);
    }

    /**
     * 创建一个新的 URL，修改 host 和 port
     */
    public URL setAddress(String host, int port) {
        return new URL(protocol, host, port, path, parameters);
    }

    /**
     * 构建器入口
     */
    public static URLBuilder builder() {
        return new URLBuilder();
    }

    // ==================== 服务键 ====================

    /**
     * 生成服务唯一标识（接口名 + 版本 + 分组）
     * 例如：com.axon.demo.IUserService:1.0.0
     */
    public String getServiceKey() {
        String version = getParameter("version", "");
        String group = getParameter("group", "");
        StringBuilder key = new StringBuilder();
        if (group != null && !group.isEmpty()) {
            key.append(group).append("/");
        }
        key.append(path);
        if (version != null && !version.isEmpty()) {
            key.append(":").append(version);
        }
        return key.toString();
    }

    /**
     * 生成地址字符串
     */
    public String getAddress() {
        return host + ":" + port;
    }

    // ==================== toString ====================

    @Override
    public String toString() {
        if (string == null) {
            StringBuilder sb = new StringBuilder();
            sb.append(protocol).append("://").append(host).append(":").append(port).append("/").append(path);
            if (!parameters.isEmpty()) {
                sb.append("?");
                boolean first = true;
                for (Map.Entry<String, String> entry : parameters.entrySet()) {
                    if (!first) sb.append("&");
                    sb.append(entry.getKey()).append("=").append(entry.getValue());
                    first = false;
                }
            }
            string = sb.toString();
        }
        return string;
    }

    // ==================== Getter ====================

    public String getProtocol() { return protocol; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getPath() { return path; }
    public Map<String, String> getParameters() { return parameters; }

    // ==================== hashCode / equals ====================

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof URL)) return false;
        URL other = (URL) obj;
        return this.toString().equals(other.toString());
    }

    /**
     * URL 构建器
     */
    public static class URLBuilder {
        private String protocol = "dubbo";
        private String host = "localhost";
        private int port = 20880;
        private String path;
        private Map<String, String> parameters = new HashMap<>();

        public URLBuilder protocol(String protocol) { this.protocol = protocol; return this; }
        public URLBuilder host(String host) { this.host = host; return this; }
        public URLBuilder port(int port) { this.port = port; return this; }
        public URLBuilder path(String path) { this.path = path; return this; }
        public URLBuilder addParameter(String key, String value) {
            this.parameters.put(key, value); return this;
        }
        public URL build() {
            return new URL(protocol, host, port, path, parameters);
        }
    }
}
