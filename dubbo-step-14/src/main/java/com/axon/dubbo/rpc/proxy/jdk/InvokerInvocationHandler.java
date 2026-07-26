package com.axon.dubbo.rpc.proxy.jdk;

import com.axon.dubbo.rpc.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * JDK 动态代理的 InvocationHandler（Step 05 升级版）
 *
 * 关键变化：不再持有 ObjectClient，改为持有 Invoker。
 * 所有方法调用委托给 Invoker.invoke(invocation)，
 * 至于 Invoker 是远程（DubboInvoker）还是本地（AbstractProxyInvoker），
 * 代理层完全不知情也不关心 —— 这就是抽象的力量。
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class InvokerInvocationHandler implements InvocationHandler {

    private final Invoker<?> invoker;

    public InvokerInvocationHandler(Invoker<?> invoker) {
        this.invoker = invoker;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // Object 方法本地处理
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }

        // ====== 1. 构建 Invocation ======
        RpcInvocation invocation = new RpcInvocation();
        invocation.setServiceName(invoker.getInterface().getName());
        invocation.setMethodName(method.getName());
        invocation.setParameterTypes(resolveTypes(method.getParameterTypes()));
        invocation.setArguments(args != null ? args : new Object[0]);

        System.out.println("[InvokerInvocationHandler] 拦截: "
                + invocation.getMethodName() + java.util.Arrays.toString(invocation.getArguments()));

        // ====== 2. 通过 Invoker 执行调用 ======
        // 这里不关心 Invoker 是远程还是本地！
        Result result = invoker.invoke(invocation);

        // ====== 3. 处理 Result ======
        return result.recreate(); // 成功返回结果，失败抛出异常
    }

    private String[] resolveTypes(Class<?>[] types) {
        String[] names = new String[types.length];
        for (int i = 0; i < types.length; i++) names[i] = types[i].getName();
        return names;
    }
}
