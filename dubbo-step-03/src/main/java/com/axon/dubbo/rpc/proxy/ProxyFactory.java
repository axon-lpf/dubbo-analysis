package com.axon.dubbo.rpc.proxy;

import com.axon.dubbo.remoting.transport.socket.ObjectClient;

/**
 * 代理工厂接口
 *
 * 定义创建服务代理的契约。
 * 通过代理，调用本地接口方法就像在调用远程服务一样——
 * 网络通信、序列化、协议等细节全部被代理层隐藏。
 *
 * 对应官方源码：org.apache.dubbo.rpc.ProxyFactory（标记了 @SPI 注解）
 *
 * Dubbo 支持两种代理方式：
 * - JavassistProxyFactory（默认）：使用字节码生成，性能更好
 * - JdkProxyFactory：使用 JDK 动态代理，依赖少
 *
 * 本步骤先实现 JDK 方式，便于理解代理原理。
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public interface ProxyFactory {

    /**
     * 为指定接口创建代理对象
     *
     * @param interfaceClass 目标接口的 Class 对象
     * @param client         网络客户端（代理内部通过它发送 RPC 请求）
     * @param <T>            接口类型
     * @return 代理对象，可以强制转换为目标接口类型
     */
    <T> T createProxy(Class<T> interfaceClass, ObjectClient client);
}
