package com.axon.dubbo.demo;

public interface IUserService {
    User getUser(Long id);
    String getUserName(Long id);
    User saveUser(User user);
}
