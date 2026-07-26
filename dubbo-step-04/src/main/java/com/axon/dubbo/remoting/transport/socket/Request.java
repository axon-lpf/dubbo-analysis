package com.axon.dubbo.remoting.transport.socket;

import java.io.Serializable;
import java.util.Arrays;

public class Request implements Serializable {
    private static final long serialVersionUID = 1L;
    private long id;
    private String interfaceName;
    private String methodName;
    private String[] parameterTypes;
    private Object[] arguments;

    public Request() {}
    public Request(long id, String interfaceName, String methodName, String[] parameterTypes, Object[] arguments) {
        this.id = id; this.interfaceName = interfaceName; this.methodName = methodName;
        this.parameterTypes = parameterTypes; this.arguments = arguments;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getInterfaceName() { return interfaceName; }
    public void setInterfaceName(String interfaceName) { this.interfaceName = interfaceName; }
    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }
    public String[] getParameterTypes() { return parameterTypes; }
    public void setParameterTypes(String[] parameterTypes) { this.parameterTypes = parameterTypes; }
    public Object[] getArguments() { return arguments; }
    public void setArguments(Object[] arguments) { this.arguments = arguments; }

    @Override
    public String toString() {
        return "Request{id=" + id + ", iface=" + interfaceName + ", method=" + methodName + ", args=" + Arrays.toString(arguments) + "}";
    }
}
