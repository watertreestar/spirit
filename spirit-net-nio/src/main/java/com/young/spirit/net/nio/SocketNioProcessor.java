package com.young.spirit.net.nio;

import com.young.commons.allocation.AllocatedBuffer;
import com.young.spirit.net.nio.config.ProcessorConfig;

import java.io.IOException;
import java.nio.ByteBuffer;

public class SocketNioProcessor extends NioProcessor{
    public SocketNioProcessor(ProcessorConfig config) {
        super(config);
    }

    @Override
    protected int read(NioByteChannel channel, AllocatedBuffer buffer) throws IOException {
        return 0;
    }

}
