package com.axon.dubbo.demo;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.remoting.exchange.DubboCodec;
import com.axon.dubbo.remoting.transport.socket.Request;
import com.axon.dubbo.remoting.transport.socket.Response;
import com.axon.dubbo.rpc.*;
import com.axon.dubbo.rpc.protocol.DubboProtocol;
import com.axon.dubbo.rpc.protocol.Protocol;
import com.axon.dubbo.rpc.proxy.ProxyFactory;
import com.axon.dubbo.rpc.proxy.jdk.JdkProxyFactory;
import org.junit.Test;

/**
 * Step 05 测试用例
 *
 * 验证 Protocol + Codec 体系：
 * - Dubbo 协议编解码
 * - Protocol.export() 导出服务
 * - Protocol.refer() 引用服务
 * - Consumer 端通过 Invoker → Proxy 完成透明远程调用
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class ApiTest {

    /**
     * 测试1：Dubbo 协议编解码（不经过网络）
     */
    @Test
    public void testDubboCodec() throws Exception {
        DubboCodec codec = new DubboCodec();

        // 编码请求
        Request request = new Request(1L, IUserService.class.getName(),
                "getUser", new String[]{"java.lang.Long"}, new Object[]{1001L});
        byte[] encoded = codec.encode(request);

        System.out.println("========== DubboCodec 测试 ==========");
        System.out.println("原始请求: " + request);
        System.out.println("编码字节数: " + encoded.length);
        System.out.println("协议头(16B) + 数据体(" + (encoded.length - 16) + "B)");

        // 打印协议头关键字节
        System.out.printf("Magic:  0x%02X%02X\n", encoded[0], encoded[1]);
        System.out.printf("Flag:   0x%02X (请求=%s)\n", encoded[2],
                (encoded[2] & 0x80) == 0 ? "是" : "否");
        System.out.printf("Req ID: %d\n",
                ((long)encoded[4]<<56 | (long)encoded[5]<<48 | (long)encoded[6]<<40 | (long)encoded[7]<<32 |
                 (long)encoded[8]<<24 | (long)encoded[9]<<16 | (long)encoded[10]<<8 | (long)encoded[11]));

        // 解码请求
        Request decoded = (Request) codec.decode(encoded);
        assert decoded.getId() == 1L;
        assert IUserService.class.getName().equals(decoded.getInterfaceName());
        assert "getUser".equals(decoded.getMethodName());
        assert ((Long)decoded.getArguments()[0]) == 1001L;
        System.out.println("解码请求: " + decoded);

        // 编码响应
        Response response = Response.success(1L, "Hello!");
        byte[] respEncoded = codec.encode(response);
        Response decodedResp = (Response) codec.decode(respEncoded);
        assert decodedResp.isSuccess();
        assert "Hello!".equals(decodedResp.getResult());
        System.out.println("解码响应: " + decodedResp);

        System.out.println("[测试通过] DubboCodec 编解码验证成功！");
    }

    /**
     * 测试2：Protocol.export() + Protocol.refer() 端到端调用
     */
    @Test
    public void testProtocolExportRefer() throws Exception {
        int port = 20881;

        // ====== Provider 端：导出服务 ======
        Protocol protocol = new DubboProtocol();
        ProxyFactory proxyFactory = new JdkProxyFactory();

        // 构建服务 URL
        URL serviceUrl = URL.builder()
                .protocol("dubbo").host("127.0.0.1").port(port)
                .path(IUserService.class.getName())
                .addParameter("version", "1.0.0").build();

        // 1. Provider: 将实现类包装为 Invoker
        Invoker<IUserService> providerInvoker = proxyFactory.getInvoker(
                new UserServiceImpl(), IUserService.class, serviceUrl);

        // 2. Provider: export → Exporter
        Exporter<IUserService> exporter = protocol.export(providerInvoker);
        System.out.println("[测试] 服务已导出: " + serviceUrl);

        // 等待服务端线程启动
        Thread.sleep(300);

        // ====== Consumer 端：引用服务 ======
        // 3. Consumer: refer → Invoker
        Invoker<IUserService> consumerInvoker = protocol.refer(IUserService.class, serviceUrl);

        // 4. Consumer: Invoker → Proxy
        IUserService userService = proxyFactory.getProxy(consumerInvoker);

        System.out.println("========== Protocol export/refer 测试 ==========");

        // 5. 透明远程调用！
        User user = userService.getUser(1001L);
        assert user != null && user.getId().equals(1001L);
        System.out.println("[测试] getUser: " + user);

        String name = userService.getUserName(2002L);
        assert "UserName_2002".equals(name);
        System.out.println("[测试] getUserName: " + name);

        User saved = userService.saveUser(new User(null, "王五", 35, "wangwu@test.com"));
        assert saved.getId() != null;
        System.out.println("[测试] saveUser: " + saved);

        System.out.println("[测试通过] Protocol export/refer 端到端验证成功！");

        // 清理
        exporter.unexport();
    }

    /**
     * 测试3：DubboCodec 魔数校验
     */
    @Test(expected = Exception.class)
    public void testCodecMagicCheck() throws Exception {
        DubboCodec codec = new DubboCodec();
        // 构造一个魔数错误的字节数组（只有 20 字节的垃圾数据）
        byte[] invalid = new byte[20];
        invalid[0] = (byte) 0xCA; invalid[1] = (byte) 0xFE; // 不是 0xdabb
        codec.decode(invalid); // 应抛出异常
    }

    /**
     * 测试4：本地 Invoker 直接调用（不经过 Protocol）
     */
    @Test
    public void testLocalInvokerProxy() throws Throwable {
        ProxyFactory proxyFactory = new JdkProxyFactory();

        // 将本地实现包装为 Invoker
        URL localUrl = URL.builder().protocol("local").path(IUserService.class.getName()).build();
        Invoker<IUserService> invoker = proxyFactory.getInvoker(
                new UserServiceImpl(), IUserService.class, localUrl);

        // 创建代理（就像普通的本地调用一样！）
        IUserService userService = proxyFactory.getProxy(invoker);

        User user = userService.getUser(1L);
        assert "User_1".equals(user.getName());
        System.out.println("[测试通过] 本地 Invoker + 代理验证成功: " + user);
    }

    public static void main(String[] args) throws Throwable {
        ApiTest test = new ApiTest();
        test.testDubboCodec();
        test.testLocalInvokerProxy();
        test.testProtocolExportRefer();

        System.out.println("\n>>> Step 05 全部测试通过！");
    }
}
