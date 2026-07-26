package com.axon.dubbo.demo;

import com.axon.dubbo.common.extension.ExtensionLoader;
import com.axon.dubbo.demo.car.Car;
import org.junit.Test;

import java.util.List;
import java.util.Set;

/**
 * Step 13 测试用例 —— Dubbo SPI 扩展机制
 *
 * 演示：
 * 1. 按名称获取扩展
 * 2. 默认扩展
 * 3. Wrapper 自动包装（AOP）
 * 4. @Activate 自动激活
 * 5. 扩展名列表
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class ApiTest {

    /**
     * 测试1：按名称获取扩展实现
     */
    @Test
    public void testGetExtensionByName() {
        ExtensionLoader<Car> loader = ExtensionLoader.getExtensionLoader(Car.class);

        // 按名称获取
        Car benz = loader.getExtension("benz");
        Car bmw = loader.getExtension("bmw");
        Car redflag = loader.getExtension("redflag");

        System.out.println("========== 按名称获取扩展 ==========");
        System.out.println("benz: " + benz.getName() + " → " + benz.drive());
        System.out.println("bmw: " + bmw.getName() + " → " + bmw.drive());
        System.out.println("redflag: " + redflag.getName() + " → " + redflag.drive());

        assert "Benz".equals(benz.getName());
        assert "BMW".equals(bmw.getName());
        assert "RedFlag".equals(redflag.getName());

        // 缓存验证：同一名称返回同一实例
        Car benzAgain = loader.getExtension("benz");
        assert benz == benzAgain : "相同名称应返回同一实例（缓存）";

        System.out.println("[测试通过] 按名称获取扩展 ✓ (缓存验证通过)");
    }

    /**
     * 测试2：默认扩展（@SPI("benz")）
     */
    @Test
    public void testDefaultExtension() {
        ExtensionLoader<Car> loader = ExtensionLoader.getExtensionLoader(Car.class);

        Car defaultCar = loader.getDefaultExtension();
        System.out.println("========== 默认扩展 ==========");
        System.out.println("默认实现: " + defaultCar.getName());

        assert "Benz".equals(defaultCar.getName()) : "@SPI(\"benz\") 默认应为 Benz";
        System.out.println("[测试通过] 默认扩展 ✓");
    }

    /**
     * 测试3：Wrapper 自动包装（AOP）
     *
     * CarWrapper 有一个 Car 参数的构造器 → ExtensionLoader 识别为 Wrapper
     * 所有 Car 实例都会被 CarWrapper 自动包装
     */
    @Test
    public void testWrapperAOP() {
        ExtensionLoader<Car> loader = ExtensionLoader.getExtensionLoader(Car.class);

        System.out.println("========== Wrapper AOP ==========");
        Car benz = loader.getExtension("benz");

        System.out.println("drive() 返回: " + benz.drive());

        // drive() 的返回值应该包含 "[Wrapped]" 前缀（被 CarWrapper 包装）
        String result = benz.drive();
        assert result.contains("[Wrapped]") : "Wrapper 应在前/后置添加 [Wrapped]";
        assert result.contains("Benz") : "原始逻辑应保留";

        System.out.println("[测试通过] Wrapper AOP ✓ (CarWrapper 自动包装了 BenzCar)");
    }

    /**
     * 测试4：@Activate 自动激活
     */
    @Test
    public void testActivate() {
        ExtensionLoader<Car> loader = ExtensionLoader.getExtensionLoader(Car.class);

        System.out.println("========== @Activate 自动激活 ==========");
        List<Car> active = loader.getActivateExtension("car");

        System.out.println("自动激活的扩展数: " + active.size());
        for (Car car : active) {
            System.out.println("  → " + car.getName() + ": " + car.drive());
        }

        assert !active.isEmpty() : "group='car' 应有 @Activate 扩展";
        System.out.println("[测试通过] @Activate ✓");
    }

    /**
     * 测试5：获取所有支持的扩展名
     */
    @Test
    public void testSupportedExtensions() {
        ExtensionLoader<Car> loader = ExtensionLoader.getExtensionLoader(Car.class);

        System.out.println("========== 支持的扩展名 ==========");
        Set<String> names = loader.getSupportedExtensions();
        for (String name : names) {
            System.out.println("  - " + name);
        }

        assert names.contains("benz");
        assert names.contains("bmw");
        assert names.contains("redflag");
        assert names.size() >= 4 : "至少应有 benz, bmw, redflag, f1";

        System.out.println("[测试通过] 扩展名列表 ✓ (共 " + names.size() + " 个)");
    }

    /**
     * 测试6：扩展点接口必须标记 @SPI
     */
    @Test(expected = IllegalArgumentException.class)
    public void testSPIAnnotationRequired() {
        // IUserService 没有 @SPI 注解 → 应抛出异常
        ExtensionLoader.getExtensionLoader(IUserService.class);
    }

    /**
     * 测试7：不存在的扩展名
     */
    @Test(expected = IllegalStateException.class)
    public void testUnknownExtension() {
        ExtensionLoader<Car> loader = ExtensionLoader.getExtensionLoader(Car.class);
        loader.getExtension("toyota"); // 不存在的扩展名
    }

    public static void main(String[] args) {
        ApiTest t = new ApiTest();
        System.out.println("========== Step 13 SPI 扩展机制 ==========\n");

        t.testGetExtensionByName();
        t.testDefaultExtension();
        t.testWrapperAOP();
        t.testActivate();
        t.testSupportedExtensions();

        System.out.println("\n>>> SPI 扩展机制核心验证通过！");

        System.out.println("\n=== 错误场景验证 ===");
        try { t.testSPIAnnotationRequired(); System.out.println("[失败]"); }
        catch (IllegalArgumentException e) { System.out.println("✓ @SPI 校验通过: " + e.getMessage().substring(0, 40) + "..."); }

        try { t.testUnknownExtension(); System.out.println("[失败]"); }
        catch (IllegalStateException e) { System.out.println("✓ 未知扩展名校验通过: " + e.getMessage().substring(0, 40) + "..."); }

        System.out.println("\n>>> Step 13 全部测试通过！");
    }
}
