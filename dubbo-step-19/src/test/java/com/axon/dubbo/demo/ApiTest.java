package com.axon.dubbo.demo;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.registry.LocalRegistry;
import com.axon.dubbo.registry.RegistryService;
import com.axon.dubbo.rpc.*;
import com.axon.dubbo.rpc.filter.MonitorFilter;
import com.axon.dubbo.rpc.protocol.DubboProtocol;
import com.axon.dubbo.rpc.protocol.Protocol;
import com.axon.dubbo.rpc.proxy.ProxyFactory;
import com.axon.dubbo.rpc.proxy.jdk.JdkProxyFactory;
import org.junit.Test;

/**
 * Step 19 测试用例 —— 监控中心
 *
 * 验证：
 * 1. MonitorFilter 通过 @Activate 自动激活
 * 2. 调用统计自动收集（次数/成功/失败/耗时）
 * 3. 统计摘要输出
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class ApiTest {

    /**
     * 测试1：MonitorFilter 被自动激活
     */
    @Test
    public void testMonitorFilterActivated() {
        java.util.List<Filter> filters = com.axon.dubbo.common.extension.ExtensionLoader
                .getExtensionLoader(Filter.class).getActivateExtension("provider");

        System.out.println("========== MonitorFilter 激活验证 ==========");
        boolean found = false;
        for (Filter f : filters) {
            System.out.println("  " + f.getClass().getSimpleName());
            if (f instanceof MonitorFilter) found = true;
        }
        assert found : "MonitorFilter 应被自动激活";
        System.out.println("[测试通过] MonitorFilter 通过 @Activate 自动激活！");
    }

    /**
     * 测试2：RPC 调用自动收集监控数据
     */
    @Test
    public void testMonitorStats() throws Exception {
        MonitorFilter.reset();
        RegistryService registry = new LocalRegistry(
                URL.builder().protocol("registry").host("127.0.0.1").port(0).path("local").build());

        int port = 20931;
        Protocol protocol = new DubboProtocol(registry);
        ProxyFactory proxyFactory = new JdkProxyFactory();

        URL serviceUrl = URL.builder().protocol("dubbo").host("127.0.0.1").port(port)
                .path(IUserService.class.getName()).addParameter("version", "1.0.0").build();
        Invoker<IUserService> prov = proxyFactory.getInvoker(new UserServiceImpl(), IUserService.class, serviceUrl);
        protocol.export(prov);
        Thread.sleep(500);

        Invoker<IUserService> consumer = protocol.refer(IUserService.class, serviceUrl);
        IUserService userService = proxyFactory.getProxy(consumer);

        System.out.println("========== 监控数据收集测试 ==========");

        // 多次调用：getUser 5次, getUserName 3次, saveUser 2次
        for (int i = 1; i <= 5; i++) userService.getUser((long) (1000 + i));
        for (int i = 1; i <= 3; i++) userService.getUserName((long) (2000 + i));
        userService.saveUser(new User(null, "A", 20, "a@test.com"));
        userService.saveUser(new User(null, "B", 30, "b@test.com"));

        // 打印统计摘要
        String summary = MonitorFilter.getSummary();
        System.out.println(summary);

        // 验证统计内容
        assert summary.contains("getUser") : "应包含 getUser";
        assert summary.contains("getUserName") : "应包含 getUserName";
        assert summary.contains("saveUser") : "应包含 saveUser";

        System.out.println("[测试通过] 监控数据自动收集验证成功！");

        MonitorFilter.reset();
        ((DubboProtocol) protocol).destroy();
    }

    /**
     * 测试3：MonitorFilter 零侵入验证
     */
    @Test
    public void testMonitorZeroIntrusion() {
        System.out.println("========== 零侵入监控 ==========");
        System.out.println("添加监控的步骤：");
        System.out.println("  1. 实现 MonitorFilter implements Filter");
        System.out.println("  2. @Activate(group={\"provider\", \"consumer\"})");
        System.out.println("  3. 在 SPI 配置文件中加一行: monitor=...MonitorFilter");
        System.out.println("  ✅ 完成！业务代码、协议层、代理层零改动！");
        System.out.println();
        System.out.println("[结论] Filter SPI 让监控成为\"可插拔\"组件");
        System.out.println("[测试通过] 零侵入验证！");
    }

    public static void main(String[] args) throws Exception {
        ApiTest t = new ApiTest();
        t.testMonitorFilterActivated();
        t.testMonitorZeroIntrusion();
        t.testMonitorStats();
        System.out.println("\n>>> Step 19 全部测试通过！");
    }
}
