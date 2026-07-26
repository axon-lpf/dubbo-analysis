package com.axon.dubbo.demo.car;

/**
 * 宝马汽车
 */
public class BmwCar implements Car {
    @Override public String getName() { return "BMW"; }
    @Override public String drive() { return "BMW is driving fast..."; }
}
