package com.young.spirit.net;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.List;
import java.util.Set;

/**
 * Process I/O read and write
 * <p>Key component to produce channel event and deal channel data.
 * <p>Listen specific event and dispatch them to handlers.
 */
public interface Processor<C extends Channel> {
    void registerChannel(C channel);
}
