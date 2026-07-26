package com.axon.dubbo.demo;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.common.extension.ExtensionLoader;
import com.axon.dubbo.registry.LocalRegistry;
import com.axon.dubbo.registry.RegistryService;
import com.axon.dubbo.rpc.*;
import com.axon.dubbo.rpc.protocol.DubboProtocol;
import com.axon.dubbo.rpc.protocol.Protocol;
import com.axon.dubbo.rpc.proxy.ProxyFactory;
import com.axon.dubbo.rpc.proxy.jdk.JdkProxyFactory;
import org.junit.Test;

import java.util.List;

/**
 * Step 14 测试用例 —— Filter 过滤器链
 *
 * 验证：
 * 1. SPI 加载 Filter
 * 2. Provider 端 Filter 链（AccessLogFilter + ExceptionFilter）
 * 3. Consumer 端 Filter 链（TimeCostFilter）
 * 4. 端到端调用 + Filter 链效果
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class ApiTest {

    /**
     * 测试1：SPI 加载 Filter 扩展
     */
    @Test
    public void testFilterSPILoading() {
        ExtensionLoader<Filter> loader = ExtensionLoader.getExtensionLoader(Filter.class);
        System.out.println("========== Filter SPI 加载 ==========");
        for (String name : loader.getSupportedExtensions()) {
            Filter f = loader.getExtension(name);
            System.out.println("  " + name + " → " + f.getClass().getSimpleName());
        }
        assert loader.getSupportedExtensions().size() >= 3;
        System.out.println("[测试通过] Filter SPI 加载成功！");
    }

    /**
     * 测试2：Provider 端 Filter 链自动激活
     */
    @Test
    public void testProviderFilterChain() {
        List<Filter> filters = ExtensionLoader.getExtensionLoader(Filter.class)
                .getActivateExtension("provider");

        System.out.println("========== Provider 端 Filter 链 ==========");
        for (Filter f : filters) {
            System.out.println("  " + f.getClass().getSimpleName()
                    + " (order=" + f.getClass()
                    .getAnnotation(com.axon.dubbo.common.extension.Activate.class).order() + ")");
        }

        assert !filters.isEmpty() : "Provider 端应有自动激活的 Filter";
        System.out.println("[测试通过] Provider 端 Filter 链共 " + filters.size() + " 个！");
    }

    /**
     * 测试3：Consumer 端 Filter 链自动激活
     */
    @Test
    public void testConsumerFilterChain() {
        List<Filter> filters = ExtensionLoader.getExtensionLoader(Filter.class)
                .getActivateExtension("consumer");

        System.out.println("========== Consumer 端 Filter 链 ==========");
        for (Filter f : filters) {
            System.out.println("  " + f.getClass().getSimpleName()
                    + " (order=" + f.getClass()
                    .getAnnotation(com.axon.dubbo.common.extension.Activate.class).order() + ")");
        }

        assert !filters.isEmpty() : "Consumer 端应有自动激活的 Filter";
        System.out.println("[测试通过] Consumer 端 Filter 链共 " + filters.size() + " 个！");
    }

    /**
     * 测试4：端到端调用 + Filter 链效果
     */
    @Test
    public void testEndToEndWithFilter() throws Exception {
        RegistryService registry = new LocalRegistry(
                URL.builder().protocol("registry").host("127.0.0.1").port(0).path("local").build());

        int port = 20911;
        Protocol protocol = new DubboProtocol(registry);
        ProxyFactory proxyFactory = new JdkProxyFactory();

        // Provider
        URL serviceUrl = URL.builder().protocol("dubbo").host("127.0.0.1").port(port)
                .path(IUserService.class.getName()).addParameter("version", "1.0.0").build();
        Invoker<IUserService> prov = proxyFactory.getInvoker(new UserServiceImpl(), IUserService.class, serviceUrl);
        Exporter<IUserService> exporter = protocol.export(prov);
        Thread.sleep(200);

        // Consumer
        Invoker<IUserService> consumer = protocol.refer(IUserService.class, serviceUrl);
        IUserService userService = proxyFactory.getProxy(consumer);

        System.out.println("========== 端到端 Filter 链调用 ==========");

        // AccessLogFilter 打印调用日志
        // ExceptionFilter 包装异常
        // TimeCostFilter 打印耗时
        User user = userService.getUser(1001L);
        assert "User_1001".equals(user.getName());
        System.out.println("[结果] " + user);

        System.out.println("[测试通过] Filter 链端到端验证成功！");

        exporter.unexport();
        registry.unregister(serviceUrl);
    }

    public static void main(String[] args) throws Exception {
        ApiTest t = new ApiTest();
        t.testFilterSPILoading();
        t.testProviderFilterChain();
        t.testConsumerFilterChain();
        t.testEndToEndWithFilter();
        System.out.println("\n>>> Step 14 全部测试通过！");
    }
}
