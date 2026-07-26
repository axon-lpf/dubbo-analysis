package com.axon.dubbo.demo.car;

import com.axon.dubbo.common.extension.Adaptive;
import com.axon.dubbo.common.extension.SPI;

/**
 * 汽车接口 —— SPI 扩展点演示
 *
 * @SPI("benz") 表示默认使用 benz 实现
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
@SPI("benz")
public interface Car {

    /**
     * 获取汽车品牌名称
     */
    String getName();

    /**
     * 驾驶
     * @Adaptive 标记表示此方法可通过 URL 参数动态选择实现
     */
    @Adaptive({"car.type"})
    String drive();
}
