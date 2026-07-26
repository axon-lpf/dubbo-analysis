package com.axon.dubbo.rpc.cluster.directory;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.rpc.Invoker;

import java.util.Arrays;
import java.util.List;

/**
 * 静态服务目录
 *
 * Invoker 列表由使用者手动指定，创建后不会变化。
 * 适用于：
 * - 直连场景（跳过注册中心，直接配置 Provider 地址）
 * - 测试场景（固定 Provider 列表）
 * - 不需要动态发现的简单场景
 *
 * 对应官方源码：org.apache.dubbo.rpc.cluster.directory.StaticDirectory
 *
 * @param <T> 服务接口类型
 * @author axon-dubbo
 * @since 1.0.0
 */
public class StaticDirectory<T> extends AbstractDirectory<T> {

    /**
     * 用固定的 Invoker 列表创建静态目录
     */
    @SafeVarargs
    public StaticDirectory(Class<T> interfaceClass, URL consumerUrl, Invoker<T>... invokers) {
        super(interfaceClass, consumerUrl);
        setInvokers(Arrays.asList(invokers));
        System.out.println("[StaticDirectory] 创建静态目录: " + interfaceClass.getSimpleName()
                + " | Provider 数量: " + invokers.length);
    }

    /**
     * 用固定的 Invoker 列表创建静态目录
     */
    public StaticDirectory(Class<T> interfaceClass, URL consumerUrl, List<Invoker<T>> invokers) {
        super(interfaceClass, consumerUrl);
        setInvokers(invokers);
        System.out.println("[StaticDirectory] 创建静态目录: " + interfaceClass.getSimpleName()
                + " | Provider 数量: " + invokers.size());
    }
}
