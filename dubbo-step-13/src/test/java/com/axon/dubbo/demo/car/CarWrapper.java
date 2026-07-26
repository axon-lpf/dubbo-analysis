package com.axon.dubbo.demo.car;

/**
 * 汽车包装器 —— 演示 SPI 的 AOP（Wrapper）机制
 *
 * 构造器参数为 Car 类型 → ExtensionLoader 将其识别为 Wrapper
 * 所有 Car 实现都会被此 Wrapper 自动包装
 */
public class CarWrapper implements Car {

    /** 被包装的原始 Car 实例 */
    private final Car car;

    public CarWrapper(Car car) {
        this.car = car;
    }

    @Override
    public String getName() {
        return car.getName();
    }

    @Override
    public String drive() {
        System.out.println("[CarWrapper - 前置] 准备驾驶 " + car.getName());
        String result = car.drive();
        System.out.println("[CarWrapper - 后置] 驾驶结束");
        return "[Wrapped] " + result;
    }
}
