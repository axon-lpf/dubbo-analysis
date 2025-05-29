package com.axon.dubbo.consumer;


import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ConsumerClient {
    public static void main(String[] args) throws Exception {
        Socket socket = new Socket("localhost", 12345);

        try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            // 发送方法名和参数
            out.writeObject("sayHello");
            out.writeObject("大飞哥");
            out.flush();

            // 接收并输出结果
            String result = (String) in.readObject();
            System.out.println("收到服务端返回：" + result);
        }
    }
}
