package com.axon.dubbo.registry;

/**
 * @author：liupengfei
 * @date：2025/5/27
 * @description：
 */
public class RegistryException extends RuntimeException {
    public RegistryException() { super(); }
    public RegistryException(String message) { super(message); }
    public RegistryException(String message, Throwable cause) { super(message, cause); }
    public RegistryException(Throwable cause) { super(cause); }
}