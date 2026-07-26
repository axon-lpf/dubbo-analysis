package com.axon.dubbo.demo.car;

import com.axon.dubbo.common.extension.Activate;

/**
 * 厂商滤镜 —— 演示 @Activate 自动激活
 *
 * @Activate(group = "car", order = 10) → 当获取 "car" 组的激活扩展时自动包含
 */
@Activate(group = "car", order = 10)
public class CarFilter1 implements Car {
    @Override public String getName() { return "Filter1"; }
    @Override public String drive() { return "CarFilter1 applied"; }
}
