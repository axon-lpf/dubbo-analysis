package com.axon.dubbo.remoting.transport.socket;

/**
 * RPC 请求对象
 *
 * 封装一次远程调用的所有必要信息。
 * 在 Step 01 中，我们只传递一个简单的消息字符串；
 * 后续步骤会扩展为携带接口名、方法名、参数类型、参数值等完整调用元数据。
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class Request {

    /**
     * 请求唯一标识，用于异步场景下匹配请求与响应
     */
    private long id;

    /**
     * 请求携带的数据（Step 01 为简单字符串消息）
     */
    private String data;

    public Request() {
    }

    public Request(long id, String data) {
        this.id = id;
        this.data = data;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "Request{id=" + id + ", data='" + data + "'}";
    }
}
