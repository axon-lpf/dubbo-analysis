package com.axon.dubbo.demo;

import java.io.Serializable;

/**
 * 用户实体（测试用）
 *
 * 注意：这个类必须实现 Serializable，因为 User 对象需要
 * 从服务端序列化后通过网络传输到客户端，客户端再反序列化还原。
 *
 * 在 Dubbo 实际使用中，所有的入参和返回值类型都建议实现 Serializable。
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private Integer age;
    private String email;

    public User() {
    }

    public User(Long id, String name, Integer age, String email) {
        this.id = id;
        this.name = name;
        this.age = age;
        this.email = email;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    @Override
    public String toString() {
        return "User{id=" + id + ", name='" + name + "', age=" + age + ", email='" + email + "'}";
    }
}
