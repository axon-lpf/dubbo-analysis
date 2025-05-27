package com.axon.dubbo.provider;

import com.axon.dubbo.api.HelloService;

/**
 * @author：liupengfei
 * @date：2025/5/26
 * @description：
 */
public class HelloServiceImpl implements HelloService {
    @Override
    public String sayHello(String name) {
        return "你好，武林至尊" + name + "！";
    }
}
