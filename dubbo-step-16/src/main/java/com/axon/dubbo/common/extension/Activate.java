package com.axon.dubbo.common.extension;

import java.lang.annotation.*;

/**
 * 自动激活注解
 *
 * 标记在扩展实现类上，符合条件时自动加载。
 *
 * group:  指定在 consumer 端还是 provider 端激活
 * value:  指定 URL 参数条件（如 {"key1", "key2"} 表示 URL 中包含 key1=任意值 时激活）
 * order:  排序编号，越小越靠前
 *
 * 应用场景：Filter 自动加载
 *   @Activate(group = {"provider"}, order = 100)
 *   public class ExceptionFilter implements Filter { ... }
 *
 *   Provider 端启动时，ExtensionLoader 自动加载所有 group="provider" 的 Filter，
 *   按 order 排序后组成过滤器链。
 *
 * 对应官方源码：org.apache.dubbo.common.extension.Activate
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Activate {

    /**
     * 指定在哪种角色下激活
     * "consumer" — 消费端
     * "provider" — 提供端
     */
    String[] group() default {};

    /**
     * URL 参数条件，URL 中必须包含这些 key 才会激活
     */
    String[] value() default {};

    /**
     * 排序编号，值越小优先级越高（在列表中的位置越靠前）
     */
    int order() default 0;
}
