package com.axon.dubbo.common.extension;

import java.lang.annotation.*;

/**
 * SPI 注解（Service Provider Interface）
 *
 * 标记一个接口为 Dubbo 扩展点。
 * value 指定默认扩展名（如 @SPI("random") 表示默认使用 RandomLoadBalance）。
 *
 * 与 JDK SPI 的区别：
 * 1. Dubbo SPI 按需加载（按名称），JDK SPI 全量加载
 * 2. Dubbo SPI 支持默认值，JDK SPI 不支持
 * 3. Dubbo SPI 支持 AOP（Wrapper）、IOC（set注入）、自适应代理
 *
 * 对应官方源码：org.apache.dubbo.common.extension.SPI
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface SPI {

    /**
     * 默认扩展实现的名称
     * 例如 @SPI("random") → getExtension(null) 或 getDefaultExtension() 返回 "random" 实现
     */
    String value() default "";
}
