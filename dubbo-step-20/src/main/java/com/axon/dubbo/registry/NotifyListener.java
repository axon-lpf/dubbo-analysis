package com.axon.dubbo.registry;

import com.axon.dubbo.common.URL;
import java.util.List;

/**
 * 注册中心变更通知监听器
 *
 * 当注册中心中某个服务的 Provider 列表发生变化时，
 * 注册中心会回调此监听器的 notify() 方法。
 *
 * 对应官方源码：org.apache.dubbo.registry.NotifyListener
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public interface NotifyListener {

    /**
     * 服务变更通知
     *
     * @param urls 变更后的服务 URL 列表（全量推送）
     */
    void notify(List<URL> urls);
}
