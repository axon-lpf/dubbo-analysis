package com.axon.dubbo.common;

import java.io.Serializable;
import java.util.*;

public class URL implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String protocol;
    private final String host;
    private final int port;
    private final String path;
    private final Map<String, String> parameters;
    private transient String string;

    public URL(String protocol, String host, int port, String path) {
        this(protocol, host, port, path, new HashMap<>());
    }

    public URL(String protocol, String host, int port, String path, Map<String, String> parameters) {
        this.protocol = protocol; this.host = host; this.port = port; this.path = path;
        this.parameters = Collections.unmodifiableMap(new HashMap<>(parameters));
    }

    public String getParameter(String key) { return parameters.get(key); }
    public String getParameter(String key, String defaultValue) {
        String v = parameters.get(key); return v != null ? v : defaultValue;
    }
    public int getParameter(String key, int defaultValue) {
        String v = parameters.get(key);
        if (v == null || v.isEmpty()) return defaultValue;
        return Integer.parseInt(v);
    }

    public URL addParameter(String key, String value) {
        if (key == null || value == null) return this;
        Map<String, String> m = new HashMap<>(parameters); m.put(key, value);
        return new URL(protocol, host, port, path, m);
    }

    public URL setAddress(String host, int port) {
        return new URL(protocol, host, port, path, parameters);
    }

    public String getServiceKey() {
        String version = getParameter("version", "");
        String group = getParameter("group", "");
        StringBuilder sb = new StringBuilder();
        if (group != null && !group.isEmpty()) sb.append(group).append("/");
        sb.append(path);
        if (version != null && !version.isEmpty()) sb.append(":").append(version);
        return sb.toString();
    }

    public String getAddress() { return host + ":" + port; }
    public String getProtocol() { return protocol; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getPath() { return path; }
    public Map<String, String> getParameters() { return parameters; }

    @Override
    public String toString() {
        if (string == null) {
            StringBuilder sb = new StringBuilder();
            sb.append(protocol).append("://").append(host).append(":").append(port).append("/").append(path);
            if (!parameters.isEmpty()) {
                sb.append("?");
                boolean first = true;
                for (Map.Entry<String, String> e : parameters.entrySet()) {
                    if (!first) sb.append("&");
                    sb.append(e.getKey()).append("=").append(e.getValue());
                    first = false;
                }
            }
            string = sb.toString();
        }
        return string;
    }

    public static URLBuilder builder() { return new URLBuilder(); }

    public static class URLBuilder {
        private String protocol = "dubbo"; private String host = "localhost"; private int port = 20880;
        private String path; private Map<String, String> parameters = new HashMap<>();
        public URLBuilder protocol(String p) { protocol = p; return this; }
        public URLBuilder host(String h) { host = h; return this; }
        public URLBuilder port(int p) { port = p; return this; }
        public URLBuilder path(String p) { path = p; return this; }
        public URLBuilder addParameter(String k, String v) { parameters.put(k, v); return this; }
        public URL build() { return new URL(protocol, host, port, path, parameters); }
    }
}
