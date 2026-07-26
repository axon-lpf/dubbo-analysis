package com.axon.dubbo.rpc.protocol;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.remoting.Codec;
import com.axon.dubbo.remoting.exchange.DubboCodec;
import com.axon.dubbo.remoting.transport.socket.Request;
import com.axon.dubbo.remoting.transport.socket.Response;
import com.axon.dubbo.rpc.Invocation;
import com.axon.dubbo.rpc.Result;
import com.axon.dubbo.rpc.RpcResult;
import com.axon.dubbo.rpc.support.AbstractInvoker;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Consumer 端 Invoker —— 通过网络发送请求到 Provider
 *
 * 这是 Invoker 在 Consumer 端的实现。
 * doInvoke() 不执行本地反射，而是：
 * 1. 构建 Request
 * 2. 通过 DubboCodec 编码
 * 3. 发送到 Provider
 * 4. 接收响应
 * 5. 解码 → 转换为 Result
 *
 * 对应官方源码：org.apache.dubbo.rpc.protocol.dubbo.DubboInvoker
 *
 * @param <T> 服务接口类型
 * @author axon-dubbo
 * @since 1.0.0
 */
public class DubboInvoker<T> extends AbstractInvoker<T> {

    private static final AtomicLong REQUEST_ID = new AtomicLong(0);
    private final Codec codec;

    public DubboInvoker(Class<T> type, URL url) {
        super(type, url);
        this.codec = new DubboCodec();
    }

    public DubboInvoker(Class<T> type, URL url, Codec codec) {
        super(type, url);
        this.codec = codec;
    }

    @Override
    protected Result doInvoke(Invocation invocation) throws Throwable {
        // 1. 构建 Request
        Request request = new Request(
                REQUEST_ID.incrementAndGet(),
                invocation.getServiceName(),
                invocation.getMethodName(),
                invocation.getParameterTypes(),
                invocation.getArguments()
        );

        URL url = getUrl();
        System.out.println("[DubboInvoker] 发起远程调用: " + request
                + " → " + url.getAddress());

        // 2. 编码请求（Request → Dubbo 协议字节数组）
        byte[] encodedRequest = codec.encode(request);

        // 3. 发送并接收（使用带长度前缀的 Socket 传输）
        try (Socket socket = new Socket(url.getHost(), url.getPort());
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {

            // 发送：长度前缀 + 协议消息
            dos.writeInt(encodedRequest.length);
            dos.write(encodedRequest);
            dos.flush();

            // 接收：长度前缀 + 协议消息
            int msgLength = dis.readInt();
            byte[] msgBytes = new byte[msgLength];
            dis.readFully(msgBytes);

            // 4. 解码响应（Dubbo 协议字节数组 → Response）
            Response response = (Response) codec.decode(msgBytes);

            // 5. Response → Result
            if (response.isSuccess()) {
                return new RpcResult(response.getResult());
            } else {
                return new RpcResult(new RuntimeException(response.getErrorMessage()));
            }
        }
    }
}
