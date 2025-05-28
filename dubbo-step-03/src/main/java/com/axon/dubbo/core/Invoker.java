
package com.axon.dubbo.core;

public interface Invoker<T> {
    Class<T> getInterface();
    Result invoke(Invocation invocation);
}
