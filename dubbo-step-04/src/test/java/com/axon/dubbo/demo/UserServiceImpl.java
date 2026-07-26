package com.axon.dubbo.demo;

public class UserServiceImpl implements IUserService {
    @Override
    public User getUser(Long id) {
        System.out.println("[UserServiceImpl] getUser(" + id + ")");
        return new User(id, "User_" + id, 25, "user" + id + "@example.com");
    }

    @Override
    public String getUserName(Long id) {
        System.out.println("[UserServiceImpl] getUserName(" + id + ")");
        return "UserName_" + id;
    }

    @Override
    public User saveUser(User user) {
        System.out.println("[UserServiceImpl] saveUser(" + user + ")");
        user.setId(System.currentTimeMillis());
        return user;
    }
}
