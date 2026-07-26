package com.axon.dubbo.demo;

import java.io.Serializable;

public class User implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id; private String name; private Integer age; private String email;
    public User() {}
    public User(Long id, String name, Integer age, String email) {
        this.id = id; this.name = name; this.age = age; this.email = email;
    }
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public String getName() { return name; } public void setName(String n) { name = n; }
    public Integer getAge() { return age; } public void setAge(Integer a) { age = a; }
    public String getEmail() { return email; } public void setEmail(String e) { email = e; }
    @Override
    public String toString() {
        return "User{id=" + id + ", name='" + name + "', age=" + age + ", email='" + email + "'}";
    }
}
