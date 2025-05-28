
package com.axon.dubbo.transport;


import com.axon.dubbo.core.Invocation;
import com.axon.dubbo.core.Invoker;
import com.axon.dubbo.core.RpcResult;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import com.axon.dubbo.core.Result;

public class RemoteInvoker<T> implements Invoker<T> {
    private final Class<T> interfaceClass;
    private final String host;
    private final int port;

    public RemoteInvoker(Class<T> interfaceClass, String host, int port) {
        this.interfaceClass = interfaceClass;
        this.host = host;
        this.port = port;
    }

    public Class<T> getInterface() {
        return interfaceClass;
    }

    public Result invoke(Invocation invocation) {
        try (
            Socket socket = new Socket(host, port);
            ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream input = new ObjectInputStream(socket.getInputStream())
        ) {
            output.writeUTF(invocation.getMethodName());
            output.writeObject(invocation.getParameterTypes());
            output.writeObject(invocation.getArguments());

            Object result = input.readObject();
            return new RpcResult(result);
        } catch (Exception e) {
            throw new RuntimeException("远程调用失败", e);
        }
    }
}
