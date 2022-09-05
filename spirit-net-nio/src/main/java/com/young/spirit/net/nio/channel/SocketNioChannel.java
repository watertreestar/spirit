package com.young.spirit.net.nio.channel;

import com.young.spirit.net.nio.NioByteChannel;
import com.young.spirit.net.nio.NioProcessor;
import com.young.spirit.net.nio.config.ChannelConfig;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;

public class SocketNioChannel extends NioByteChannel {
    private SocketChannel socketChannel;

    public SocketNioChannel(NioProcessor processor, ChannelConfig channelConfig) {
        super(processor, channelConfig);
    }

    public SocketNioChannel(NioProcessor processor, ChannelConfig channelConfig, SocketChannel socketChannel) {
        super(processor, channelConfig);
        this.socketChannel = socketChannel;
    }

    public void setChannel(java.nio.channels.SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
    }

    @Override
    protected SelectableChannel realChannel() {
        return socketChannel;
    }

    @Override
    protected int write(ByteBuffer buf) throws IOException {
        return 0;
    }
}
