package com.axon.dubbo.rpc.support;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.rpc.*;

public abstract class AbstractInvoker<T> implements Invoker<T> {
    private final Class<T> type;
    private final URL url;
    private volatile boolean available = true;

    public AbstractInvoker(Class<T> type, URL url) {
        if (type == null) throw new IllegalArgumentException("type不能为null");
        if (url == null) throw new IllegalArgumentException("url不能为null");
        this.type = type; this.url = url;
    }

    @Override public Class<T> getInterface() { return type; }
    @Override public URL getUrl() { return url; }
    @Override public boolean isAvailable() { return available; }
    @Override public void destroy() { available = false; }

    @Override
    public Result invoke(Invocation invocation) {
        if (!available) return new RpcResult(new IllegalStateException("Invoker不可用"));
        try { return doInvoke(invocation); }
        catch (Throwable e) { return new RpcResult(e); }
    }

    protected abstract Result doInvoke(Invocation invocation) throws Throwable;
}
