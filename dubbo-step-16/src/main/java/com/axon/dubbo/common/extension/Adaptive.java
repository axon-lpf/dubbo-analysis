package com.axon.dubbo.common.extension;

import java.lang.annotation.*;

/**
 * 自适应扩展注解
 *
 * 可以标记在类上或接口方法上：
 *
 * 标记在类上：
 *   该类本身就是自适应实现，ExtensionLoader 直接使用它。
 *   例如：AdaptiveExtensionFactory
 *
 * 标记在接口方法上：
 *   ExtensionLoader 动态生成代理类，代理类在运行时根据 URL 参数
 *   决定调用哪个扩展实现。
 *   例如：Protocol 接口的 export() 方法上标记 @Adaptive({"protocol"})
 *         → 运行时从 URL 读取 "protocol" 参数 → 加载对应 Protocol 实现
 *
 * 对应官方源码：org.apache.dubbo.common.extension.Adaptive
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Adaptive {

    /**
     * 从 URL 中提取扩展名的参数 key 列表
     * 按顺序尝试，取第一个有值的参数值作为扩展名
     *
     * 例如 @Adaptive({"protocol"}) → URL.getParameter("protocol")
     */
    String[] value() default {};
}
