package com.axon.dubbo.demo.car;

/**
 * 红旗汽车
 */
public class RedFlagCar implements Car {
    @Override public String getName() { return "RedFlag"; }
    @Override public String drive() { return "RedFlag is driving with honor..."; }
}
