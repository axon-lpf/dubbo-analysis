package com.axon.dubbo.rpc.proxy;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.remoting.transport.socket.ObjectClient;
import com.axon.dubbo.rpc.Invoker;

/**
 * 代理工厂接口
 *
 * 提供两个方向的核心能力：
 *
 * 1. getProxy(Invoker) → 创建 Consumer 端代理
 *    将 Invoker（远程调用能力）包装为本地接口代理
 *
 * 2. getInvoker(proxy, type, url) → 创建 Provider 端 Invoker
 *    将本地实现类包装为 Invoker（可被远程调用）
 *
 * 对应官方源码：org.apache.dubbo.rpc.ProxyFactory（@SPI）
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public interface ProxyFactory {

    /**
     * 创建 Consumer 端代理（远程调用 → 本地接口）
     *
     * @param interfaceClass 服务接口
     * @param client         网络客户端
     * @param <T>            接口类型
     * @return 代理对象
     */
    <T> T createProxy(Class<T> interfaceClass, ObjectClient client);

    /**
     * 创建 Provider 端 Invoker（本地实现 → 可远程调用）
     *
     * @param proxy 服务实现类实例
     * @param type  服务接口类型
     * @param url   服务 URL
     * @param <T>   接口类型
     * @return Invoker
     */
    <T> Invoker<T> getInvoker(T proxy, Class<T> type, URL url);
}
