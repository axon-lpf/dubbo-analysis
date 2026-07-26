package com.axon.dubbo.remoting.transport.socket;

import java.io.Serializable;

/**
 * RPC 响应对象（支持序列化）
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class Response implements Serializable {

    private static final long serialVersionUID = 1L;

    private long id;
    private boolean success;
    private Object result;
    private String errorMessage;
    private String exceptionClassName;

    public Response() {
    }

    public static Response success(long id, Object result) {
        Response response = new Response();
        response.setId(id);
        response.setSuccess(true);
        response.setResult(result);
        return response;
    }

    public static Response error(long id, String errorMessage, String exceptionClassName) {
        Response response = new Response();
        response.setId(id);
        response.setSuccess(false);
        response.setErrorMessage(errorMessage);
        response.setExceptionClassName(exceptionClassName);
        return response;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public Object getResult() { return result; }
    public void setResult(Object result) { this.result = result; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getExceptionClassName() { return exceptionClassName; }
    public void setExceptionClassName(String exceptionClassName) { this.exceptionClassName = exceptionClassName; }

    @Override
    public String toString() {
        if (success) {
            return "Response{id=" + id + ", success=true, result=" + result + '}';
        } else {
            return "Response{id=" + id + ", success=false, error=" + errorMessage + '}';
        }
    }
}
