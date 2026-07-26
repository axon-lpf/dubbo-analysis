package com.axon.dubbo.rpc.proxy;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.rpc.*;
import com.axon.dubbo.rpc.support.AbstractInvoker;

import java.lang.reflect.Method;

/**
 * Provider 端 Invoker —— 通过反射调用本地服务实现类
 *
 * 这是 Provider 端的默认 Invoker 实现。
 * doInvoke() 通过 Java 反射找到目标方法并执行。
 *
 * 工作流程：
 * 1. 根据 invocation.getMethodName() 找到对应的 Method 对象
 * 2. 通过 method.invoke(proxy, arguments) 执行
 * 3. 将返回值包装为 RpcResult
 *
 * 对应官方源码：org.apache.dubbo.rpc.proxy.AbstractProxyInvoker
 *
 * @param <T> 服务接口类型
 * @author axon-dubbo
 * @since 1.0.0
 */
public class AbstractProxyInvoker<T> extends AbstractInvoker<T> {

    /**
     * 实际的服务实现类实例
     */
    private final T proxy;

    public AbstractProxyInvoker(T proxy, Class<T> type, URL url) {
        super(type, url);
        this.proxy = proxy;
    }

    /**
     * 通过反射调用目标方法
     */
    @Override
    protected Result doInvoke(Invocation invocation) throws Throwable {
        // 1. 解析参数类型
        String[] paramTypeNames = invocation.getParameterTypes();
        Class<?>[] parameterTypes = new Class<?>[paramTypeNames != null ? paramTypeNames.length : 0];
        if (paramTypeNames != null) {
            for (int i = 0; i < paramTypeNames.length; i++) {
                parameterTypes[i] = resolveClass(paramTypeNames[i]);
            }
        }

        // 2. 查找目标方法
        Method method = proxy.getClass().getMethod(
                invocation.getMethodName(),
                parameterTypes);

        // 3. 反射调用
        Object[] args = invocation.getArguments();
        Object result = method.invoke(proxy, args != null ? args : new Object[0]);

        // 4. 封装结果
        return new RpcResult(result);
    }

    /**
     * 解析类名 → Class 对象
     */
    private Class<?> resolveClass(String className) throws ClassNotFoundException {
        switch (className) {
            case "boolean": return boolean.class;
            case "byte":    return byte.class;
            case "short":   return short.class;
            case "int":     return int.class;
            case "long":    return long.class;
            case "float":   return float.class;
            case "double":  return double.class;
            case "char":    return char.class;
            case "void":    return void.class;
            default:        return Class.forName(className);
        }
    }
}
