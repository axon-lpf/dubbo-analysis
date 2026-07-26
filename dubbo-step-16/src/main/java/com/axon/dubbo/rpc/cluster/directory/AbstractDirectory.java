package com.axon.dubbo.rpc.cluster.directory;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.rpc.Invocation;
import com.axon.dubbo.rpc.Invoker;
import com.axon.dubbo.rpc.cluster.Directory;

import java.util.Collections;
import java.util.List;

/**
 * 服务目录抽象基类
 *
 * 提供 Directory 接口的默认实现骨架。
 * 子类只需关心"Invoker 列表从何而来"，无需重复实现接口管理和 URL 管理。
 *
 * 两种典型子类：
 * - RegistryDirectory: Invoker 列表来自注册中心（动态订阅）
 * - StaticDirectory:   Invoker 列表来自手动配置（固定不变）
 *
 * 对应官方源码：org.apache.dubbo.rpc.cluster.directory.AbstractDirectory
 *
 * @param <T> 服务接口类型
 * @author axon-dubbo
 * @since 1.0.0
 */
public abstract class AbstractDirectory<T> implements Directory<T> {

    private final Class<T> interfaceClass;
    private final URL consumerUrl;

    /**
     * 当前可用的 Invoker 列表（volatile 保证多线程可见性）
     */
    protected volatile List<Invoker<T>> invokers = Collections.emptyList();

    public AbstractDirectory(Class<T> interfaceClass, URL consumerUrl) {
        this.interfaceClass = interfaceClass;
        this.consumerUrl = consumerUrl;
    }

    @Override
    public Class<T> getInterface() {
        return interfaceClass;
    }

    @Override
    public URL getConsumerUrl() {
        return consumerUrl;
    }

    @Override
    public List<Invoker<T>> list(Invocation invocation) {
        return invokers;
    }

    /**
     * 使用不可变列表替换当前 Invoker 列表
     */
    protected void setInvokers(List<Invoker<T>> newInvokers) {
        this.invokers = Collections.unmodifiableList(newInvokers);
    }
}
