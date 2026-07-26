package com.axon.dubbo.config;

/**
 * 协议配置
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class ProtocolConfig {
    private String name = "dubbo";
    private String host = "127.0.0.1";
    private int port = 20880;

    public ProtocolConfig() {}
    public ProtocolConfig(int port) { this.port = port; }

    public String getName() { return name; }
    public void setName(String n) { name = n; }
    public String getHost() { return host; }
    public void setHost(String h) { host = h; }
    public int getPort() { return port; }
    public void setPort(int p) { port = p; }
}
