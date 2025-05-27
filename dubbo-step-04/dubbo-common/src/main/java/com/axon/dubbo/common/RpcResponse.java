package com.axon.dubbo.common;

import lombok.Data;

import java.io.Serializable;

/**
 * @author：liupengfei
 * @date：2025/5/26
 * @description：
 */
@Data
public class RpcResponse implements Serializable {
    private Object result;
    private Exception exception;
}