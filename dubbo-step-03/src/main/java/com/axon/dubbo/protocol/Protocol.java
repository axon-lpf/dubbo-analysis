
package com.axon.dubbo.protocol;

import com.axon.dubbo.core.Invoker;

public interface Protocol {
    <T> Exporter<T> export(Invoker<T> invoker);
    <T> Invoker<T> refer(Class<T> interfaceClass, String host, int port);
}
