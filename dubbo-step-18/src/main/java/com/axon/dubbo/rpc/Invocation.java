package com.axon.dubbo.rpc;

public interface Invocation {
    String getServiceName();
    String getMethodName();
    String[] getParameterTypes();
    Object[] getArguments();
    java.util.Map<String, Object> getAttachments();
    Object getAttachment(String key);
}
