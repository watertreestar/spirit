package com.young.spirit.net.nio.config;

import lombok.Data;

@Data
public class ChannelConfig {
    /**
     * Buffer size of per reading channel
     * <p>Change dynamically</p>
     */
    private int readBufferSize = 1024;


}
