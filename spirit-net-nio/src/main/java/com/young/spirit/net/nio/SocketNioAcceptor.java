package com.young.spirit.net.nio;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;

/**
 * Acceptor implementation of TCP protocol
 */
public class SocketNioAcceptor extends NioAcceptor{

    @Override
    protected void acceptInternal(SelectionKey key) throws IOException {

    }

    @Override
    protected SelectableChannel bindInternal(SocketAddress address) throws IOException {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.bind(address);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        return serverSocketChannel;
    }
}
