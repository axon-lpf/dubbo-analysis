package com.axon.dubbo.demo;

import com.axon.dubbo.config.DubboBootstrap;
import com.axon.dubbo.config.ReferenceConfig;
import com.axon.dubbo.config.RegistryConfig;
import com.axon.dubbo.config.ServiceConfig;
import com.axon.dubbo.rpc.filter.MonitorFilter;
import org.junit.Test;

/**
 * Step 22 集成测试 —— 全架构整合
 *
 * 一次性启动 Provider + Consumer，
 * 验证所有层的协作：Config → Protocol → Filter → Cluster → Netty → Registry
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class ApiTest {

    /**
     * 完整集成测试：一站式启动
     */
    @Test
    public void testFullIntegration() throws Exception {
        MonitorFilter.reset();

        // ====== 配置 Provider ======
        ServiceConfig<IUserService> serviceConfig = new ServiceConfig<>();
        serviceConfig.setInterface(IUserService.class);
        serviceConfig.setRef(new UserServiceImpl());
        serviceConfig.setRegistry(new RegistryConfig("local", 0));
        serviceConfig.setVersion("2.0.0");

        // ====== 配置 Consumer ======
        ReferenceConfig<IUserService> referenceConfig = new ReferenceConfig<>();
        referenceConfig.setInterface(IUserService.class);
        referenceConfig.setRegistry(new RegistryConfig("local", 0));
        referenceConfig.setVersion("2.0.0");

        // ====== 一键启动！ ======
        DubboBootstrap.getInstance()
                .service(serviceConfig)
                .reference(referenceConfig)
                .start();

        // ====== 使用服务 ======
        IUserService userService = referenceConfig.get();

        System.out.println("========== 全架构整合测试 ==========");

        // 3 种不同的方法调用
        User user = userService.getUser(1001L);
        System.out.println("[getUser] " + user);

        String name = userService.getUserName(2002L);
        System.out.println("[getUserName] " + name);

        User saved = userService.saveUser(new User(null, "最终测试", 99, "final@test.com"));
        System.out.println("[saveUser] " + saved);

        // 验证
        assert "User_1001".equals(user.getName()) : "getUser 失败";
        assert "UserName_2002".equals(name) : "getUserName 失败";
        assert saved.getId() != null : "saveUser 失败";

        System.out.println();
        System.out.println("[测试通过] 全架构整合——DubboBootstrap 一站式启动成功！");

        // 打印监控统计
        System.out.println(MonitorFilter.getSummary());

        // 清理
        DubboBootstrap.getInstance().stop();
    }

    /**
     * 设计模式总结（以文档形式展示）
     */
    @Test
    public void testDesignPatterns() {
        System.out.println("========== Dubbo 设计模式全景 ==========\n");

        String[][] patterns = {
            {"工厂模式", "ProxyFactory, RegistryConfig.createRegistry()", "创建对象，隐藏实例化细节"},
            {"单例模式", "ExtensionLoader 扩展实例缓存, DubboBootstrap", "全局唯一实例"},
            {"代理模式", "InvokerInvocationHandler (JDK Proxy)", "透明化远程调用"},
            {"模板方法", "AbstractInvoker.invoke() → doInvoke()", "定义算法骨架，子类实现步骤"},
            {"责任链", "Filter.invoke(invoker, inv) → 调用下一个", "横切关注点可插拔"},
            {"观察者", "NotifyListener → Registry.notify()", "Provider 变更自动通知"},
            {"装饰器", "CarWrapper(Car) → Filter 链包装 Invoker", "不改变接口，增强功能"},
            {"适配器", "Netty → DubboCodec → LengthFieldBasedFrameDecoder", "不同接口适配"},
            {"策略", "LoadBalance: Random/RoundRobin/LeastActive/CH", "算法可替换"},
            {"外观", "DubboBootstrap.start() → 一键启动", "简化复杂子系统"},
            {"建造者", "URL.builder().protocol().host().port().build()", "流式构建复杂对象"},
            {"微内核", "SPI + ExtensionLoader + @Activate", "内核稳定，插件灵活"},
        };

        for (String[] p : patterns) {
            System.out.printf("  %-10s  %-45s  %s\n", p[0], p[1], p[2]);
        }

        System.out.println("\n[总结] 12 种设计模式，构建了一个完整的微内核+插件化 RPC 框架！");
    }

    public static void main(String[] args) throws Exception {
        ApiTest t = new ApiTest();
        t.testDesignPatterns();
        t.testFullIntegration();
        System.out.println("\n>>> Step 22 全系列完成！");
    }
}
