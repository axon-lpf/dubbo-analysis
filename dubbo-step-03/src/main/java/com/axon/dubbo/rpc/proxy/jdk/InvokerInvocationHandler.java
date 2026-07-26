package com.axon.dubbo.rpc.proxy.jdk;

import com.axon.dubbo.remoting.transport.socket.ObjectClient;
import com.axon.dubbo.remoting.transport.socket.Request;
import com.axon.dubbo.remoting.transport.socket.Response;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JDK 动态代理的 InvocationHandler 实现
 *
 * 这是整个客户端代理机制的核心。
 * 每当通过代理对象调用方法时，JVM 都会调用本类的 invoke() 方法。
 *
 * 工作流程：
 * 1. 拦截方法调用（被代理接口上的任何方法调用都会进入这里）
 * 2. 过滤 Object 类的方法（toString、hashCode、equals 等本地执行）
 * 3. 将方法信息包装为 Request 对象
 * 4. 通过 ObjectClient 发送到服务端
 * 5. 从 Response 中提取结果并返回给调用方
 *
 * 对调用方来说，整个过程完全透明——
 * 他们看到的就是普通的 UserService.getUser(1001L) 调用。
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class InvokerInvocationHandler implements InvocationHandler {

    /**
     * 目标接口
     */
    private final Class<?> interfaceClass;

    /**
     * 网络客户端
     */
    private final ObjectClient client;

    /**
     * 请求 ID 生成器（线程安全自增）
     */
    private static final AtomicLong REQUEST_ID = new AtomicLong(0);

    public InvokerInvocationHandler(Class<?> interfaceClass, ObjectClient client) {
        this.interfaceClass = interfaceClass;
        this.client = client;
    }

    /**
     * 代理方法拦截
     *
     * @param proxy  代理对象本身
     * @param method 被调用的方法
     * @param args   方法参数
     * @return 方法返回值（来自远程服务端）
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // ====== 1. 过滤 Object 类的方法 ======
        // toString、hashCode、equals 等方法不需要远程调用，本地处理即可
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }

        // ====== 2. 提取方法元数据 ======
        String methodName = method.getName();
        Class<?>[] parameterTypes = method.getParameterTypes();
        String[] paramTypeNames = new String[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            paramTypeNames[i] = parameterTypes[i].getName();
        }

        System.out.println("[InvokerInvocationHandler] 拦截方法调用: "
                + interfaceClass.getSimpleName() + "." + methodName
                + "(" + java.util.Arrays.toString(args) + ")");

        // ====== 3. 构造 RPC 请求 ======
        Request request = new Request(
                REQUEST_ID.incrementAndGet(),
                interfaceClass.getName(),  // 接口全限定名
                methodName,                // 方法名
                paramTypeNames,            // 参数类型
                args != null ? args : new Object[0]  // 参数值
        );

        // ====== 4. 发送请求并获取响应 ======
        Response response = client.send(request);

        // ====== 5. 处理响应结果 ======
        if (response.isSuccess()) {
            System.out.println("[InvokerInvocationHandler] 远程调用成功，返回值: "
                    + response.getResult());
            return response.getResult();
        } else {
            // 将服务端异常转换为本地异常抛出
            System.err.println("[InvokerInvocationHandler] 远程调用失败: "
                    + response.getErrorMessage());
            throw new RuntimeException(
                    "RPC 调用失败 [" + interfaceClass.getName() + "." + methodName + "]: "
                            + response.getErrorMessage());
        }
    }
}
