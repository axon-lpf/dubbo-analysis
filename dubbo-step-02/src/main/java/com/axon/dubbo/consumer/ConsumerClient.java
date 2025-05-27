package com.axon.dubbo.consumer;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * @author：liupengfei
 * @date：2025/5/27
 * @description：
 */
public class ConsumerClient {

    public static void main(String[] args) {
        try (
                Socket socket = new Socket("localhost", 9000);
                ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream input = new ObjectInputStream(socket.getInputStream())
        ) {
            // 模拟远程调用
            output.writeObject("sayHello");
            output.writeObject("World");

            // 接收响应
            String result = (String) input.readObject();
            System.out.println("客户端收到返回值：" + result);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
