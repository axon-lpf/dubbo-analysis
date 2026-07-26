package com.axon.dubbo.rpc.proxy.jdk;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.remoting.transport.socket.ObjectClient;
import com.axon.dubbo.rpc.Invoker;
import com.axon.dubbo.rpc.proxy.AbstractProxyInvoker;
import com.axon.dubbo.rpc.proxy.ProxyFactory;

import java.lang.reflect.Proxy;

/**
 * JDK 动态代理工厂（Step 04 升级版）
 *
 * 新增 getInvoker() 方法：将本地实现类包装为 Provider 端 Invoker
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class JdkProxyFactory implements ProxyFactory {

    @Override
    @SuppressWarnings("unchecked")
    public <T> T createProxy(Class<T> interfaceClass, ObjectClient client) {
        if (!interfaceClass.isInterface()) {
            throw new IllegalArgumentException("JDK 动态代理只能代理接口: " + interfaceClass.getName());
        }
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                new InvokerInvocationHandler(interfaceClass, client));
    }

    @Override
    public <T> Invoker<T> getInvoker(T proxy, Class<T> type, URL url) {
        return new AbstractProxyInvoker<T>(proxy, type, url) {
            // doInvoke 已在 AbstractProxyInvoker 中实现（反射调用）
        };
    }
}
