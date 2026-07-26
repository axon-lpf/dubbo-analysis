package com.axon.dubbo.rpc;

import java.io.Serializable;
import java.util.Arrays;

/**
 * RPC 调用元数据封装
 *
 * 当客户端通过代理调用方法时，InvocationHandler 会拦截调用，
 * 将方法信息封装为 RpcInvocation，再通过网络发送到服务端。
 *
 * 对应官方源码：org.apache.dubbo.rpc.RpcInvocation
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class RpcInvocation implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 目标接口全限定名
     */
    private String interfaceName;

    /**
     * 方法名
     */
    private String methodName;

    /**
     * 参数类型（全限定类名数组）
     */
    private String[] parameterTypes;

    /**
     * 参数值
     */
    private Object[] arguments;

    /**
     * 返回值类型
     */
    private String returnType;

    public RpcInvocation() {
    }

    public RpcInvocation(String interfaceName, String methodName,
                         String[] parameterTypes, Object[] arguments, String returnType) {
        this.interfaceName = interfaceName;
        this.methodName = methodName;
        this.parameterTypes = parameterTypes;
        this.arguments = arguments;
        this.returnType = returnType;
    }

    // ==================== Getter/Setter ====================

    public String getInterfaceName() { return interfaceName; }
    public void setInterfaceName(String interfaceName) { this.interfaceName = interfaceName; }

    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }

    public String[] getParameterTypes() { return parameterTypes; }
    public void setParameterTypes(String[] parameterTypes) { this.parameterTypes = parameterTypes; }

    public Object[] getArguments() { return arguments; }
    public void setArguments(Object[] arguments) { this.arguments = arguments; }

    public String getReturnType() { return returnType; }
    public void setReturnType(String returnType) { this.returnType = returnType; }

    @Override
    public String toString() {
        return "RpcInvocation{interfaceName='" + interfaceName
                + "', methodName='" + methodName
                + "', args=" + Arrays.toString(arguments) + '}';
    }
}
