package com.axon.dubbo.demo;

import com.axon.dubbo.config.ReferenceConfig;
import com.axon.dubbo.config.RegistryConfig;
import com.axon.dubbo.config.ServiceConfig;
import org.junit.Test;

/**
 * Step 20 测试用例 —— 配置层
 *
 * 验证声明式配置 API 极大简化 Provider/Consumer 启动流程
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class ApiTest {

    /**
     * 测试1：ServiceConfig 一键导出服务
     */
    @Test
    public void testServiceConfig() throws Exception {
        ServiceConfig<IUserService> service = new ServiceConfig<>();
        service.setInterface(IUserService.class);
        service.setRef(new UserServiceImpl());
        service.setRegistry(new RegistryConfig("local", 0));
        service.setVersion("1.0.0");
        service.export();

        System.out.println("========== ServiceConfig ==========");
        System.out.println("服务已导出，等待 Consumer 调用...");
        Thread.sleep(500);

        // Consumer 使用 ReferenceConfig
        ReferenceConfig<IUserService> reference = new ReferenceConfig<>();
        reference.setInterface(IUserService.class);
        reference.setRegistry(new RegistryConfig("local", 0));

        IUserService userService = reference.get();  // 一行获取代理！

        User user = userService.getUser(1001L);
        System.out.println("[结果] " + user);

        assert "User_1001".equals(user.getName());
        System.out.println("[测试通过] ServiceConfig/ReferenceConfig 声明式 API 验证成功！");

        service.unexport();
    }

    /**
     * 测试2：对比手动流程 vs 声明式流程
     */
    @Test
    public void testConfigVsManual() {
        System.out.println("========== 手动 vs 声明式 对比 ==========\n");

        System.out.println("【Step 19 手动流程 —— 10+ 行代码】");
        System.out.println("  RegistryService registry = new LocalRegistry(...);");
        System.out.println("  Protocol protocol = new DubboProtocol(registry);");
        System.out.println("  ProxyFactory proxyFactory = new JdkProxyFactory();");
        System.out.println("  URL url = URL.builder()...build();");
        System.out.println("  Invoker<T> inv = proxyFactory.getInvoker(impl, ...);");
        System.out.println("  Exporter<T> exporter = protocol.export(inv);");
        System.out.println("  // Consumer 也需要类似 6 行...\n");

        System.out.println("【Step 20 声明式流程 —— 4 行代码】");
        System.out.println("  ServiceConfig<T> service = new ServiceConfig<>();");
        System.out.println("  service.setInterface(IXxx.class);");
        System.out.println("  service.setRef(new XxxImpl());");
        System.out.println("  service.export();  // 一键启动！\n");

        System.out.println("  ReferenceConfig<T> ref = new ReferenceConfig<>();");
        System.out.println("  ref.setInterface(IXxx.class);");
        System.out.println("  T proxy = ref.get();  // 一行获取代理！");
        System.out.println("  proxy.method();  // 透明 RPC 调用\n");

        System.out.println("[结论] 配置层封装了 Registry/Protocol/ProxyFactory/URL 等底层细节");
        System.out.println("[测试通过] 从 10+ 行到 4 行，简化 60%！");
    }

    /**
     * 测试3：ReferenceConfig 缓存（多次 get 返回同一对象）
     */
    @Test
    public void testReferenceCache() throws Exception {
        ServiceConfig<IUserService> service = new ServiceConfig<>();
        service.setInterface(IUserService.class);
        service.setRef(new UserServiceImpl());
        service.setRegistry(new RegistryConfig("local", 0));
        service.export();
        Thread.sleep(500);

        ReferenceConfig<IUserService> reference = new ReferenceConfig<>();
        reference.setInterface(IUserService.class);
        reference.setRegistry(new RegistryConfig("local", 0));

        IUserService proxy1 = reference.get();
        IUserService proxy2 = reference.get();
        assert proxy1 == proxy2 : "ReferenceConfig 应缓存代理对象";

        System.out.println("========== 代理缓存验证 ==========");
        System.out.println("proxy1 == proxy2: " + (proxy1 == proxy2));
        System.out.println("[测试通过] ReferenceConfig 代理缓存机制！");

        service.unexport();
    }

    public static void main(String[] args) throws Exception {
        ApiTest t = new ApiTest();
        t.testConfigVsManual();
        t.testServiceConfig();
        t.testReferenceCache();
        System.out.println("\n>>> Step 20 全部测试通过！");
    }
}
