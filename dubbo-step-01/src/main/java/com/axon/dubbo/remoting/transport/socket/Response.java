package com.axon.dubbo.remoting.transport.socket;

/**
 * RPC 响应对象
 *
 * 封装服务端处理请求后返回的结果。
 * 在 Step 01 中只携带简单的响应字符串，
 * 后续步骤会扩展为携带返回值对象、异常信息等。
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class Response {

    /**
     * 对应请求的 ID，用于关联
     */
    private long id;

    /**
     * 响应数据（Step 01 为简单字符串）
     */
    private String data;

    public Response() {
    }

    public Response(long id, String data) {
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
        return "Response{id=" + id + ", data='" + data + "'}";
    }
}
