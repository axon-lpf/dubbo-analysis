package com.axon.dubbo.core;

/**
 * @author：liupengfei
 * @date：2025/5/28
 * @description：
 */
public class RpcResult implements Result{

    private final Object value;

    public RpcResult(Object value) {
        this.value = value;
    }

    public Object getValue() { return value; }
}
