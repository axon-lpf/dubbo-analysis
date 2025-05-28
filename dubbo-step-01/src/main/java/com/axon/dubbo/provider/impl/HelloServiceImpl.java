package com.axon.dubbo.provider.impl;

import com.axon.dubbo.api.HelloService;

/**
 * @author：liupengfei
 * @date：2025/5/28
 * @description：
 */
public class HelloServiceImpl implements HelloService {

    @Override
    public String sayHello(String name) {
        return "Hello, " + name + "!";
    }
}