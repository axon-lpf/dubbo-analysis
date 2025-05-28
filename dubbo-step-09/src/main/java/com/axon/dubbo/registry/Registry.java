package com.axon.dubbo.registry;

import java.util.List;

/**
 * @author：liupengfei
 * @date：2025/5/27
 * @description：
 */
public interface Registry {

    /**
     * 服务注册
     *
     * @param serviceName    服务名称（一般是接口全限定名）
     * @param serviceAddress 服务地址（ip:port）
     */
    void register(String serviceName, String serviceAddress) throws RegistryException;

    /**
     * 服务注销
     *
     * @param serviceName
     * @param serviceAddress
     */
    void unregister(String serviceName, String serviceAddress) throws RegistryException;

    /**
     * 查询服务所有可用地址
     *
     * @param serviceName
     * @return 服务地址列表
     */
    List<String> lookup(String serviceName) throws RegistryException;
}
