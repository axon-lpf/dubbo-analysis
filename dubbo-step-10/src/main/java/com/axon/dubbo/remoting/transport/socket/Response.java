package com.axon.dubbo.remoting.transport.socket;

import java.io.Serializable;

public class Response implements Serializable {
    private static final long serialVersionUID = 1L;
    private long id;
    private boolean success;
    private Object result;
    private String errorMessage;
    private String exceptionClassName;

    public Response() {}
    public static Response success(long id, Object result) {
        Response r = new Response(); r.id = id; r.success = true; r.result = result; return r;
    }
    public static Response error(long id, String msg, String exClass) {
        Response r = new Response(); r.id = id; r.success = false; r.errorMessage = msg; r.exceptionClassName = exClass; return r;
    }
    public long getId() { return id; } public void setId(long id) { this.id = id; }
    public boolean isSuccess() { return success; } public void setSuccess(boolean s) { success = s; }
    public Object getResult() { return result; } public void setResult(Object r) { result = r; }
    public String getErrorMessage() { return errorMessage; } public void setErrorMessage(String m) { errorMessage = m; }
    public String getExceptionClassName() { return exceptionClassName; } public void setExceptionClassName(String c) { exceptionClassName = c; }
    @Override
    public String toString() {
        return success ? "Response{id=" + id + ", ok, res=" + result + "}"
                : "Response{id=" + id + ", err=" + errorMessage + "}";
    }
}
