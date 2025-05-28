package com.axon.dubbo.core.codec;

import lombok.Data;

import java.io.Serializable;

/**
 * @author：liupengfei
 * @date：2025/5/27
 * @description：
 */
@Data
public class RpcResponse  implements Serializable {

    private Object result;
    private Exception exception;

    public RpcResponse() {}

    public RpcResponse(Object result) {
        this.result = result;
    }

    public RpcResponse(Exception exception) {
        this.exception = exception;
    }

    public boolean hasException() {
        return exception != null;
    }

}
