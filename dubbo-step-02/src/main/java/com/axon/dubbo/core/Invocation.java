
package com.axon.dubbo.core;

import java.io.Serializable;

public interface Invocation extends Serializable {
    String getMethodName();
    Class<?>[] getParameterTypes();
    Object[] getArguments();
}
