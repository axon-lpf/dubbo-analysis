package com.axon.dubbo.rpc;

/**
 * RPC 调用接口
 *
 * 封装一次 RPC 调用的所有元数据，是 Invoker 的入参。
 * 在 Dubbo 中，这是最核心的接口之一，贯穿整个调用链路。
 *
 * 对应官方源码：org.apache.dubbo.rpc.Invocation
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public interface Invocation {

    /**
     * 获取目标接口全限定名
     */
    String getServiceName();

    /**
     * 获取方法名
     */
    String getMethodName();

    /**
     * 获取参数类型（全限定类名数组）
     */
    String[] getParameterTypes();

    /**
     * 获取方法参数值
     */
    Object[] getArguments();

    /**
     * 获取隐式参数（Attachment）
     * 用于在 consumer 和 provider 之间传递隐式上下文信息
     */
    java.util.Map<String, Object> getAttachments();

    /**
     * 获取指定隐式参数
     */
    Object getAttachment(String key);
}
