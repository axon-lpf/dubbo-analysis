package com.axon.dubbo.provider.impl;

import com.axon.dubbo.api.IHelloService;

/**
 * @author：liupengfei
 * @date：2025/5/26
 * @description：
 */
public class HelloServiceImpl implements IHelloService {
    @Override
    public String sayHello(String name) {
        return "你好，武林至尊" + name + "！";
    }
}
