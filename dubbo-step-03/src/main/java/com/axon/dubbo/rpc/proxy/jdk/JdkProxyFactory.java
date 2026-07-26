package com.axon.dubbo.rpc.proxy.jdk;

import com.axon.dubbo.remoting.transport.socket.ObjectClient;
import com.axon.dubbo.rpc.proxy.ProxyFactory;

import java.lang.reflect.Proxy;

/**
 * JDK 动态代理工厂实现
 *
 * 使用 java.lang.reflect.Proxy 创建代理对象。
 *
 * JDK 动态代理的限制：
 * - 只能代理接口，不能代理类
 * - 被代理的接口必须至少有一个方法
 *
 * Dubbo 默认使用 Javassist 代理（可以代理类，性能更好），
 * 但 JDK 代理的原理更容易理解，适合入门学习。
 *
 * 对应官方源码：org.apache.dubbo.rpc.proxy.jdk.JdkProxyFactory
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class JdkProxyFactory implements ProxyFactory {

    @Override
    @SuppressWarnings("unchecked")
    public <T> T createProxy(Class<T> interfaceClass, ObjectClient client) {
        // 校验：必须是接口
        if (!interfaceClass.isInterface()) {
            throw new IllegalArgumentException(
                    "JDK 动态代理只能代理接口: " + interfaceClass.getName());
        }

        // 使用 JDK Proxy 创建代理实例
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),     // 类加载器
                new Class<?>[]{interfaceClass},       // 目标接口
                new InvokerInvocationHandler(interfaceClass, client)  // 调用处理器
        );
    }
}
