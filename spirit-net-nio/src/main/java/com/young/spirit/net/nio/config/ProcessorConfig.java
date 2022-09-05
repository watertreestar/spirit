package com.young.spirit.net.nio.config;

import lombok.Data;

@Data
public class ProcessorConfig {
    /**
     * Maximum flush bytes for per channel
     */
    private int maxWriteBytes;

    /**
     * Flush model, Flush oneOff or fair
     */
    private boolean fairFlush = false;

    private boolean useDirectBuffer = false;
}
