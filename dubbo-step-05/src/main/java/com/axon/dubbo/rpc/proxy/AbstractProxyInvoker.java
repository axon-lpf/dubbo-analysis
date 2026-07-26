package com.axon.dubbo.rpc.proxy;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.rpc.*;
import com.axon.dubbo.rpc.support.AbstractInvoker;
import java.lang.reflect.Method;

/**
 * Provider 端 Invoker —— 通过反射调用本地服务实现
 */
public class AbstractProxyInvoker<T> extends AbstractInvoker<T> {
    private final T proxy;

    public AbstractProxyInvoker(T proxy, Class<T> type, URL url) {
        super(type, url);
        this.proxy = proxy;
    }

    @Override
    protected Result doInvoke(Invocation invocation) throws Throwable {
        String[] paramTypeNames = invocation.getParameterTypes();
        Class<?>[] parameterTypes = new Class<?>[paramTypeNames != null ? paramTypeNames.length : 0];
        if (paramTypeNames != null) {
            for (int i = 0; i < paramTypeNames.length; i++) {
                parameterTypes[i] = resolveClass(paramTypeNames[i]);
            }
        }
        Method method = proxy.getClass().getMethod(invocation.getMethodName(), parameterTypes);
        Object[] args = invocation.getArguments();
        Object result = method.invoke(proxy, args != null ? args : new Object[0]);
        return new RpcResult(result);
    }

    private Class<?> resolveClass(String name) throws ClassNotFoundException {
        switch (name) {
            case "boolean": return boolean.class; case "byte": return byte.class;
            case "short": return short.class; case "int": return int.class;
            case "long": return long.class; case "float": return float.class;
            case "double": return double.class; case "char": return char.class;
            case "void": return void.class;
            default: return Class.forName(name);
        }
    }
}
