package com.axon.dubbo.common;

import lombok.Data;

import java.io.Serializable;

/**
 * @author：liupengfei
 * @date：2025/5/26
 * @description：
 */
@Data
public class RpcRequest implements Serializable {
    private String className;
    private String methodName;
    private Class<?>[] parameterTypes;
    private Object[] parameters;
}
