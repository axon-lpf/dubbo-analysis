package com.axon.dubbo.rpc;

import java.io.Serializable;
import java.util.*;

public class RpcInvocation implements Invocation, Serializable {
    private static final long serialVersionUID = 1L;
    private String serviceName;
    private String methodName;
    private String[] parameterTypes;
    private Object[] arguments;
    private Map<String, Object> attachments = new HashMap<>();

    public RpcInvocation() {}
    public RpcInvocation(String serviceName, String methodName, String[] parameterTypes, Object[] arguments) {
        this.serviceName = serviceName; this.methodName = methodName;
        this.parameterTypes = parameterTypes; this.arguments = arguments;
    }

    @Override public String getServiceName() { return serviceName; }
    public void setServiceName(String n) { serviceName = n; }
    @Override public String getMethodName() { return methodName; }
    public void setMethodName(String n) { methodName = n; }
    @Override public String[] getParameterTypes() { return parameterTypes; }
    public void setParameterTypes(String[] t) { parameterTypes = t; }
    @Override public Object[] getArguments() { return arguments; }
    public void setArguments(Object[] a) { arguments = a; }
    @Override public Map<String, Object> getAttachments() { return Collections.unmodifiableMap(attachments); }
    @Override public Object getAttachment(String k) { return attachments.get(k); }
    public void setAttachment(String k, Object v) { attachments.put(k, v); }

    @Override
    public String toString() {
        return "RpcInvocation{svc=" + serviceName + ", method=" + methodName + ", args=" + Arrays.toString(arguments) + "}";
    }
}
