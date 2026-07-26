package com.axon.dubbo.rpc.proxy.jdk;

import com.axon.dubbo.remoting.transport.socket.ObjectClient;
import com.axon.dubbo.remoting.transport.socket.Request;
import com.axon.dubbo.remoting.transport.socket.Response;
import com.axon.dubbo.rpc.Invocation;
import com.axon.dubbo.rpc.RpcInvocation;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JDK 动态代理的 InvocationHandler（Step 04 升级版）
 *
 * 变化：使用 Invocation / RpcInvocation 封装调用元数据，
 * 为后续引入 Invoker 体系做好准备。
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class InvokerInvocationHandler implements InvocationHandler {

    private final Class<?> interfaceClass;
    private final ObjectClient client;
    private static final AtomicLong REQUEST_ID = new AtomicLong(0);

    public InvokerInvocationHandler(Class<?> interfaceClass, ObjectClient client) {
        this.interfaceClass = interfaceClass;
        this.client = client;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // Object 方法本地处理
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }

        // ====== 1. 构建 Invocation（调用元数据） ======
        Invocation invocation = buildInvocation(method, args);
        System.out.println("[InvokerInvocationHandler] 拦截方法: "
                + invocation.getMethodName() + " "
                + (args != null ? Arrays.toString(args) : "[]"));

        // ====== 2. 构造网络请求 ======
        Request request = new Request(
                REQUEST_ID.incrementAndGet(),
                invocation.getServiceName(),
                invocation.getMethodName(),
                invocation.getParameterTypes(),
                invocation.getArguments()
        );

        // ====== 3. 发送请求并获取响应 ======
        Response response = client.send(request);

        // ====== 4. 处理结果 ======
        if (response.isSuccess()) {
            return response.getResult();
        } else {
            throw new RuntimeException("RPC 调用失败 [" + interfaceClass.getName()
                    + "." + method.getName() + "]: " + response.getErrorMessage());
        }
    }

    /**
     * 从 Method 对象构建 Invocation
     */
    private Invocation buildInvocation(Method method, Object[] args) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        String[] paramTypeNames = new String[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            paramTypeNames[i] = parameterTypes[i].getName();
        }

        RpcInvocation invocation = new RpcInvocation();
        invocation.setServiceName(interfaceClass.getName());
        invocation.setMethodName(method.getName());
        invocation.setParameterTypes(paramTypeNames);
        invocation.setArguments(args != null ? args : new Object[0]);
        return invocation;
    }
}
