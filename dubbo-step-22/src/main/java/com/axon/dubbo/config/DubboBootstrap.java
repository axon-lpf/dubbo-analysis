package com.axon.dubbo.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Dubbo 一站式启动器
 *
 * 整合所有配置，提供统一的启动/关闭入口。
 *
 * 使用示例：
 * DubboBootstrap.getInstance()
 *     .service(new ServiceConfig<>().setInterface(...).setRef(...))
 *     .reference(new ReferenceConfig<>().setInterface(...))
 *     .start();
 *
 * XxxService service = DubboBootstrap.getInstance().get(XxxService.class);
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class DubboBootstrap {

    private static final DubboBootstrap INSTANCE = new DubboBootstrap();

    private final List<ServiceConfig<?>> services = new ArrayList<>();
    private final List<ReferenceConfig<?>> references = new ArrayList<>();

    private DubboBootstrap() {}

    public static DubboBootstrap getInstance() { return INSTANCE; }

    /**
     * 注册服务
     */
    public DubboBootstrap service(ServiceConfig<?> config) {
        services.add(config);
        return this;
    }

    /**
     * 注册引用
     */
    public DubboBootstrap reference(ReferenceConfig<?> config) {
        references.add(config);
        return this;
    }

    /**
     * 一键启动所有服务和引用
     */
    public DubboBootstrap start() {
        System.out.println("\n========== DubboBootstrap 启动 ==========");
        for (ServiceConfig<?> svc : services) {
            svc.export();
        }
        for (ReferenceConfig<?> ref : references) {
            ref.get();
        }
        System.out.println("========== 启动完成 ==========\n");
        return this;
    }

    /**
     * 获取服务代理
     */
    @SuppressWarnings("unchecked")
    public <T> T getService(Class<T> type) {
        for (ReferenceConfig<?> ref : references) {
            try {
                return (T) ref.get();
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * 关闭
     */
    public void stop() {
        for (ServiceConfig<?> svc : services) {
            svc.unexport();
        }
        System.out.println("[DubboBootstrap] 已关闭");
    }
}
