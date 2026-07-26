package com.axon.dubbo.demo;

/**
 * 用户服务实现类（测试用）
 *
 * 部署在服务端，处理实际的业务逻辑。
 * 客户端永远看不到这个类，他们只知道 IUserService 接口。
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class UserServiceImpl implements IUserService {

    @Override
    public User getUser(Long id) {
        System.out.println("[UserServiceImpl] 执行 getUser(" + id + ")");
        return new User(id, "User_" + id, 25, "user" + id + "@example.com");
    }

    @Override
    public String getUserName(Long id) {
        System.out.println("[UserServiceImpl] 执行 getUserName(" + id + ")");
        return "UserName_" + id;
    }

    @Override
    public User saveUser(User user) {
        System.out.println("[UserServiceImpl] 执行 saveUser(" + user + ")");
        // 模拟保存：生成 ID 并返回
        user.setId(System.currentTimeMillis());
        return user;
    }
}
