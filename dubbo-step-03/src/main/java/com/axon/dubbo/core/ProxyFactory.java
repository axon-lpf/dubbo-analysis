package com.axon.dubbo.core;

/**
 * @author：liupengfei
 * @date：2025/5/28
 * @description：
 */
public interface ProxyFactory {
    <T> T getProxy(Invoker<T> invoker);

}
