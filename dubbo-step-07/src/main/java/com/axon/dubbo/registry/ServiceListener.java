package com.axon.dubbo.registry;

import java.util.List;

/**
 * @author：liupengfei
 * @date：2025/5/27
 * @description：
 */
public interface ServiceListener {

    void onServiceChanged(List<String> addresses);

}
