
package com.axon.dubbo.core;

public class RpcResult implements Result {
    private final Object value;

    public RpcResult(Object value) {
        this.value = value;
    }

    public Object getValue() { return value; }
}
