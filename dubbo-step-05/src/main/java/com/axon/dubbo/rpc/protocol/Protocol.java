package com.axon.dubbo.rpc.protocol;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.rpc.Exporter;
import com.axon.dubbo.rpc.Invoker;

/**
 * 协议接口 —— RPC 协议抽象
 *
 * 定义了两个核心操作，是 Dubbo 中连接 Provider 和 Consumer 的桥梁：
 *
 * 1. export(Invoker) → Exporter
 *    Provider 端：将本地服务暴露到网络上
 *    开启端口监听 → 接收请求 → 解码 → 调用 Invoker → 编码 → 返回响应
 *
 * 2. refer(Class, URL) → Invoker
 *    Consumer 端：根据 URL 创建远程调用代理 Invoker
 *    创建网络客户端 → 构建调用 Invoker（doInvoke 中完成网络请求）
 *
 * Protocol 层是整个 Dubbo 框架的"腰"：
 * 上面是 Proxy、Registry、Cluster 等业务层，
 * 下面是 Transport、Codec、Serialize 等网络层。
 *
 * 对应官方源码：org.apache.dubbo.rpc.Protocol（@SPI("dubbo")）
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public interface Protocol {

    /**
     * 导出服务（Provider 端）
     *
     * @param invoker 包装了服务实现类的 Invoker
     * @param <T>     服务接口类型
     * @return Exporter 导出器（可用于取消导出）
     */
    <T> Exporter<T> export(Invoker<T> invoker);

    /**
     * 引用服务（Consumer 端）
     *
     * @param type 服务接口类型
     * @param url  服务地址 URL（包含协议、主机、端口等信息）
     * @param <T>  服务接口类型
     * @return Invoker（调用其 invoke() 方法时会发起网络请求）
     */
    <T> Invoker<T> refer(Class<T> type, URL url);
}
