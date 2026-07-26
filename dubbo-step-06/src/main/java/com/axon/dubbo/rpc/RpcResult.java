package com.axon.dubbo.rpc;

public class RpcResult implements Result {
    private Object value;
    private Throwable exception;

    public RpcResult() {}
    public RpcResult(Object value) { this.value = value; }
    public RpcResult(Throwable exception) { this.exception = exception; }

    @Override public Object getValue() { return value; }
    public void setValue(Object v) { value = v; }
    @Override public Throwable getException() { return exception; }
    public void setException(Throwable e) { exception = e; }
    @Override public boolean hasException() { return exception != null; }
    @Override public Object recreate() throws Throwable {
        if (exception != null) throw exception;
        return value;
    }
    @Override public String toString() {
        return hasException() ? "RpcResult{ex=" + exception.getMessage() + "}" : "RpcResult{v=" + value + "}";
    }
}
