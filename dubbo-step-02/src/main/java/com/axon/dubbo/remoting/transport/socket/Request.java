package com.axon.dubbo.remoting.transport.socket;

import java.io.Serializable;
import java.util.Arrays;

/**
 * RPC 请求对象（支持序列化）
 *
 * 封装一次远程方法调用的所有必要信息。
 * 实现 Serializable 接口，使其可以通过 JDK 序列化在网络中传输。
 *
 * 对应官方源码：org.apache.dubbo.remoting.exchange.Request
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class Request implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 请求唯一标识
     *
     * 在异步通信中，客户端通过 requestId 将响应与请求关联。
     * 目前使用简单的 long 自增，后续会扩展为 AtomicLong 线程安全递增。
     */
    private long id;

    /**
     * 接口全限定名
     *
     * 例如："com.axon.dubbo.demo.UserService"
     * 服务端根据此字段在服务注册表中查找对应的实现类。
     */
    private String interfaceName;

    /**
     * 方法名
     *
     * 例如："getUser"、"updateUser"
     */
    private String methodName;

    /**
     * 参数类型（全限定类名）
     *
     * 用于方法重载时的精确匹配。
     * 例如：["java.lang.Long", "java.lang.String"]
     */
    private String[] parameterTypes;

    /**
     * 方法调用参数值
     *
     * 实际传给方法的参数值，与 parameterTypes 一一对应。
     */
    private Object[] arguments;

    public Request() {
    }

    public Request(long id, String interfaceName, String methodName,
                   String[] parameterTypes, Object[] arguments) {
        this.id = id;
        this.interfaceName = interfaceName;
        this.methodName = methodName;
        this.parameterTypes = parameterTypes;
        this.arguments = arguments;
    }

    // ==================== Getter/Setter ====================

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String[] getParameterTypes() {
        return parameterTypes;
    }

    public void setParameterTypes(String[] parameterTypes) {
        this.parameterTypes = parameterTypes;
    }

    public Object[] getArguments() {
        return arguments;
    }

    public void setArguments(Object[] arguments) {
        this.arguments = arguments;
    }

    @Override
    public String toString() {
        return "Request{" +
                "id=" + id +
                ", interfaceName='" + interfaceName + '\'' +
                ", methodName='" + methodName + '\'' +
                ", parameterTypes=" + Arrays.toString(parameterTypes) +
                ", arguments=" + Arrays.toString(arguments) +
                '}';
    }
}
