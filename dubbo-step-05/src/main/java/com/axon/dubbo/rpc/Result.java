package com.axon.dubbo.rpc;

public interface Result {
    Object getValue();
    Throwable getException();
    boolean hasException();
    Object recreate() throws Throwable;
}
