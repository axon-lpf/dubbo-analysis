package com.axon.dubbo.rpc.proxy;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.rpc.Invoker;

/**
 * 代理工厂接口（Step 05 升级版）
 *
 * 核心变化：Consumer 端代理不再直接依赖 ObjectClient，
 * 而是通过 Invoker 统一调用 —— Invoker 可以是远程的（DubboInvoker）
 * 也可以是本地的（AbstractProxyInvoker），代理层不关心。
 *
 * 对应官方源码：org.apache.dubbo.rpc.ProxyFactory（@SPI("javassist")）
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public interface ProxyFactory {

    /**
     * Consumer 端：将 Invoker 包装为本地接口代理
     *
     * 调用 proxy.method() → Invoker.invoke(invocation)
     *
     * @param invoker 远程调用 Invoker（来自 Protocol.refer()）
     * @param <T>     服务接口类型
     * @return 代理对象
     */
    <T> T getProxy(Invoker<T> invoker);

    /**
     * Provider 端：将本地实现类包装为 Invoker
     *
     * @param proxy 服务实现类实例
     * @param type  服务接口类型
     * @param url   服务 URL
     * @param <T>   接口类型
     * @return Provider 端 Invoker
     */
    <T> Invoker<T> getInvoker(T proxy, Class<T> type, URL url);
}
