package com.axon.dubbo.core.codec;

import lombok.Data;

import java.io.Serializable;

/**
 * @author：liupengfei
 * @date：2025/5/27
 * @description：
 */
@Data
public class RpcRequest  implements Serializable {

    private Long requestId;
    private String interfaceName;
    private String methodName;
    private Class<?>[] paramTypes;
    private Object[] parameters;

    // 构造器、getter/setter
    public RpcRequest() {}

    public RpcRequest(String interfaceName, String methodName, Class<?>[] paramTypes, Object[] parameters) {
        this.interfaceName = interfaceName;
        this.methodName = methodName;
        this.paramTypes = paramTypes;
        this.parameters = parameters;
    }
}
