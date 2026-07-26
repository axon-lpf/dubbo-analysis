package com.axon.dubbo.rpc.proxy.jdk;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.rpc.Invoker;
import com.axon.dubbo.rpc.proxy.AbstractProxyInvoker;
import com.axon.dubbo.rpc.proxy.ProxyFactory;
import java.lang.reflect.Proxy;

/**
 * JDK 动态代理工厂（Step 05 升级版）
 *
 * 现在两个核心方法都直接操作 Invoker：
 * - getProxy(Invoker) → 代理对象（Consumer 端）
 * - getInvoker(proxy, type, url) → Provider 端 Invoker
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class JdkProxyFactory implements ProxyFactory {

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProxy(Invoker<T> invoker) {
        return (T) Proxy.newProxyInstance(
                invoker.getInterface().getClassLoader(),
                new Class<?>[]{invoker.getInterface()},
                new InvokerInvocationHandler(invoker));
    }

    @Override
    public <T> Invoker<T> getInvoker(T proxy, Class<T> type, URL url) {
        return new AbstractProxyInvoker<T>(proxy, type, url) {};
    }
}
