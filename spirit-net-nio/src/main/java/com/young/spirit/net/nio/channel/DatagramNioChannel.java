package com.young.spirit.net.nio.channel;

import com.young.spirit.net.nio.NioByteChannel;
import com.young.spirit.net.nio.NioProcessor;
import com.young.spirit.net.nio.config.ChannelConfig;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;

public class DatagramNioChannel extends NioByteChannel {
    private DatagramChannel channel;

    public DatagramNioChannel(NioProcessor processor, ChannelConfig channelConfig) {
        super(processor, channelConfig);
    }

    @Override
    protected SelectableChannel realChannel() {
        return channel;
    }

    @Override
    protected int write(ByteBuffer buf) throws IOException {
        SocketAddress remoteSocketAddress = this.getRemoteSocketAddress();
        return channel.send(buf, remoteSocketAddress);
    }
}
