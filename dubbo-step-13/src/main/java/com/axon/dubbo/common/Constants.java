package com.axon.dubbo.common;

/**
 * Dubbo 协议常量
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public final class Constants {

    private Constants() {}

    /** Dubbo 协议魔数 */
    public static final short MAGIC = (short) 0xdabb;

    /** 消息头长度（字节） */
    public static final int HEADER_LENGTH = 16;

    // ====== 标志位（第 3 字节） ======
    /** 标志位：请求 */
    public static final byte FLAG_REQUEST = 0x00;
    /** 标志位：响应 */
    public static final byte FLAG_RESPONSE = (byte) 0x80;
    /** 标志位：单向调用（不需要返回值） */
    public static final byte FLAG_ONEWAY = 0x40;

    // ====== 序列化 ID ======
    /** JDK 序列化 */
    public static final byte SERIALIZATION_JDK = 0;
    /** Hessian2 序列化 */
    public static final byte SERIALIZATION_HESSIAN2 = 1;
    /** Fastjson 序列化 */
    public static final byte SERIALIZATION_FASTJSON = 2;

    // ====== 响应状态 ======
    public static final byte RESPONSE_OK = 20;
    public static final byte RESPONSE_ERROR = 30;
    public static final byte RESPONSE_SERVER_ERROR = 31;
}
