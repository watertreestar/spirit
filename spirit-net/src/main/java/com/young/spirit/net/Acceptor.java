package com.young.spirit.net;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.Set;

/**
 * Accepts I/O incoming request. Obviously, used by server
 */
public interface Acceptor {
    void bind(int port) throws IOException;

    void bind(SocketAddress firstLocalAddress, SocketAddress... otherLocalAddresses) throws IOException;

    void unbind(int port) throws IOException;

    void unbind(SocketAddress firstLocalAddress, SocketAddress... otherLocalAddresses) throws IOException;

    Set<SocketAddress> getBoundAddresses();
}
