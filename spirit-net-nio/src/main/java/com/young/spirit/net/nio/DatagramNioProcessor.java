package com.young.spirit.net.nio;

import com.young.commons.allocation.AllocatedBuffer;
import com.young.spirit.net.nio.config.ProcessorConfig;

import java.io.IOException;
import java.nio.ByteBuffer;

public class DatagramNioProcessor extends NioProcessor{
    public DatagramNioProcessor(ProcessorConfig config) {
        super(config);
    }

    @Override
    protected int read(NioByteChannel channel, AllocatedBuffer buffer) throws IOException {
        return 0;
    }

}
