package com.axon.dubbo.rpc;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * RPC 调用实现（Invocation 接口实现）
 *
 * 携带一次 RPC 调用所需要的全部信息。
 * 从客户端代理层创建，经过网络传输，到达服务端后被 Invoker 消费。
 *
 * 对应官方源码：org.apache.dubbo.rpc.RpcInvocation
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class RpcInvocation implements Invocation, Serializable {

    private static final long serialVersionUID = 1L;

    private String serviceName;
    private String methodName;
    private String[] parameterTypes;
    private Object[] arguments;
    private Map<String, Object> attachments = new HashMap<>();

    public RpcInvocation() {
    }

    public RpcInvocation(String serviceName, String methodName,
                         String[] parameterTypes, Object[] arguments) {
        this.serviceName = serviceName;
        this.methodName = methodName;
        this.parameterTypes = parameterTypes;
        this.arguments = arguments;
    }

    @Override
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    @Override
    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }

    @Override
    public String[] getParameterTypes() { return parameterTypes; }
    public void setParameterTypes(String[] parameterTypes) { this.parameterTypes = parameterTypes; }

    @Override
    public Object[] getArguments() { return arguments; }
    public void setArguments(Object[] arguments) { this.arguments = arguments; }

    @Override
    public Map<String, Object> getAttachments() {
        return Collections.unmodifiableMap(attachments);
    }

    @Override
    public Object getAttachment(String key) {
        return attachments.get(key);
    }

    public void setAttachment(String key, Object value) {
        this.attachments.put(key, value);
    }

    @Override
    public String toString() {
        return "RpcInvocation{service='" + serviceName
                + "', method='" + methodName
                + "', args=" + Arrays.toString(arguments) + '}';
    }
}
