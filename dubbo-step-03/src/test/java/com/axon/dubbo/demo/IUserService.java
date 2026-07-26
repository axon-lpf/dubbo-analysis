package com.axon.dubbo.demo;

/**
 * 用户服务接口（测试用）
 *
 * 这个接口定义在客户端和服务端共享的 API 包中。
 * 在 Dubbo 实际开发中，服务接口通常会抽取到独立的 jar 包，供双方依赖。
 *
 * 客户端通过代理调用这个接口的方法，
 * 服务端提供这个接口的实现类。
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public interface IUserService {

    /**
     * 根据 ID 查询用户
     */
    User getUser(Long id);

    /**
     * 根据 ID 查询用户名
     */
    String getUserName(Long id);

    /**
     * 保存用户
     */
    User saveUser(User user);
}
