package com.axon.dubbo.rpc.cluster.directory;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.registry.NotifyListener;
import com.axon.dubbo.registry.RegistryService;
import com.axon.dubbo.rpc.Invoker;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于注册中心的服务目录（Step 09 升级版）
 *
 * 继承 AbstractDirectory，复用 invokers 管理逻辑。
 *
 * 对应官方源码：org.apache.dubbo.registry.integration.RegistryDirectory
 *
 * @param <T> 服务接口类型
 * @author axon-dubbo
 * @since 1.0.0
 */
public class RegistryDirectory<T> extends AbstractDirectory<T> implements NotifyListener {

    private final RegistryService registry;
    private final InvokerFactory<T> invokerFactory;

    public RegistryDirectory(Class<T> serviceType, URL consumerUrl,
                             RegistryService registry, InvokerFactory<T> invokerFactory) {
        super(serviceType, consumerUrl);
        this.registry = registry;
        this.invokerFactory = invokerFactory;

        registry.subscribe(consumerUrl, this);
        System.out.println("[RegistryDirectory] 已订阅: " + consumerUrl.getServiceKey());
    }

    /**
     * 注册中心推送变更 → 转换 URL → 更新 invokers
     */
    @Override
    public void notify(List<URL> providerUrls) {
        System.out.println("[RegistryDirectory] 收到通知，Provider 数量: " + providerUrls.size());

        List<Invoker<T>> newInvokers = new ArrayList<>();
        for (URL url : providerUrls) {
            try {
                Invoker<T> invoker = invokerFactory.createInvoker(url);
                newInvokers.add(invoker);
                System.out.println("[RegistryDirectory]   → " + url.getAddress()
                        + " (" + url.getServiceKey() + ")");
            } catch (Exception e) {
                System.err.println("[RegistryDirectory] 创建 Invoker 失败: " + url);
            }
        }

        setInvokers(newInvokers); // AbstractDirectory 方法
        System.out.println("[RegistryDirectory] 列表已更新，共 " + newInvokers.size() + " 个 Invoker");
    }

    public void destroy() {
        registry.unsubscribe(getConsumerUrl(), this);
        System.out.println("[RegistryDirectory] 已取消订阅");
    }

    /**
     * URL → Invoker 工厂
     */
    public interface InvokerFactory<T> {
        Invoker<T> createInvoker(URL providerUrl);
    }
}
