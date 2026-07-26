package com.axon.dubbo.remoting.transport.socket;

import java.io.Serializable;

/**
 * RPC 响应对象（支持序列化）
 *
 * 封装服务端处理请求后的返回结果。
 * 实现 Serializable 接口，使其可以通过 JDK 序列化在网络中传输。
 *
 * 对应官方源码：org.apache.dubbo.remoting.exchange.Response
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class Response implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 对应请求的 ID，用于将响应与请求关联
     */
    private long id;

    /**
     * 调用是否成功
     */
    private boolean success;

    /**
     * 调用结果（成功时）
     *
     * Object 类型使得 Response 可以携带任何类型的返回值。
     * 接收方需要知道具体的返回类型才能正确转型。
     */
    private Object result;

    /**
     * 异常信息（失败时）
     *
     * 当服务端执行方法抛出异常时，将异常信息封装到此字段，
     * 客户端反序列化后可以重新抛出或处理。
     */
    private String errorMessage;

    /**
     * 异常全限定类名
     *
     * 记录原始异常类型，客户端可以根据此信息决定如何处理。
     */
    private String exceptionClassName;

    public Response() {
    }

    /**
     * 创建成功响应
     */
    public static Response success(long id, Object result) {
        Response response = new Response();
        response.setId(id);
        response.setSuccess(true);
        response.setResult(result);
        return response;
    }

    /**
     * 创建失败响应
     */
    public static Response error(long id, String errorMessage, String exceptionClassName) {
        Response response = new Response();
        response.setId(id);
        response.setSuccess(false);
        response.setErrorMessage(errorMessage);
        response.setExceptionClassName(exceptionClassName);
        return response;
    }

    // ==================== Getter/Setter ====================

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getExceptionClassName() {
        return exceptionClassName;
    }

    public void setExceptionClassName(String exceptionClassName) {
        this.exceptionClassName = exceptionClassName;
    }

    @Override
    public String toString() {
        if (success) {
            return "Response{id=" + id + ", success=true, result=" + result + '}';
        } else {
            return "Response{id=" + id + ", success=false, error=" + errorMessage + '}';
        }
    }
}
