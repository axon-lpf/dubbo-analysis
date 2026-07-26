package com.axon.dubbo.demo.car;

import com.axon.dubbo.common.extension.Activate;

/**
 * 奔驰汽车
 */
public class BenzCar implements Car {
    @Override public String getName() { return "Benz"; }
    @Override public String drive() { return "Benz is driving smoothly..."; }
}
