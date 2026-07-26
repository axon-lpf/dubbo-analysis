package com.axon.dubbo.rpc;

/**
 * RPC 调用结果实现
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class RpcResult implements Result {

    /**
     * 正常返回的值
     */
    private Object value;

    /**
     * 异常信息
     */
    private Throwable exception;

    public RpcResult() {
    }

    public RpcResult(Object value) {
        this.value = value;
    }

    public RpcResult(Throwable exception) {
        this.exception = exception;
    }

    @Override
    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    @Override
    public Throwable getException() {
        return exception;
    }

    public void setException(Throwable exception) {
        this.exception = exception;
    }

    @Override
    public boolean hasException() {
        return exception != null;
    }

    /**
     * 重建结果：有异常则抛出，无异常则返回正常值
     *
     * 这个方法让调用方可以用统一的 try-catch 处理：
     *
     * try {
     *     Object result = rpcResult.recreate();
     * } catch (Throwable t) {
     *     // 处理远程异常
     * }
     */
    @Override
    public Object recreate() throws Throwable {
        if (exception != null) {
            throw exception;
        }
        return value;
    }

    @Override
    public String toString() {
        if (hasException()) {
            return "RpcResult{exception=" + exception.getMessage() + "}";
        }
        return "RpcResult{value=" + value + "}";
    }
}
