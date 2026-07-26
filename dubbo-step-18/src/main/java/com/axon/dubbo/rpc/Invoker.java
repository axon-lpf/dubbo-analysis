package com.axon.dubbo.rpc;

import com.axon.dubbo.common.URL;

public interface Invoker<T> {
    Class<T> getInterface();
    Result invoke(Invocation invocation);
    URL getUrl();
    boolean isAvailable();
    void destroy();
}
