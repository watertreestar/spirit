package com.young.spirit.net.nio;

import com.young.commons.lifecycle.LifeCycle;
import com.young.spirit.net.Acceptor;
import com.young.spirit.net.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public abstract class NioAcceptor implements Acceptor, LifeCycle {
    private static final Logger logger = LoggerFactory.getLogger(NioAcceptor.class);
    protected final Map<SocketAddress, SelectableChannel> serverAddressChannel = new ConcurrentHashMap<>();
    private final Set<SocketAddress> bindAddress = new HashSet<>();
    private final Set<SocketAddress> unbindAddress = new HashSet<>();
    private final Object bindLock = new Object();
    private volatile boolean bindEndFlag = false;
    private volatile boolean running;
    private IOException bindException = null;
    protected Processor processor;
    /**
     * In order to listen OP_ACCEPT event
     */
    protected Selector selector;

    public NioAcceptor(){}

    public NioAcceptor(int port) {
        this(new InetSocketAddress(port));
    }

    public NioAcceptor(SocketAddress socketAddress) {
        try{
            this.bind(socketAddress);
        }catch (IOException e) {
            throw new IllegalStateException("Failed to bind");
        }
    }

    @Override
    public void bind(int port) throws IOException {
        bind(new InetSocketAddress(port));
    }

    @Override
    public void bind(SocketAddress firstLocalAddress, SocketAddress... otherLocalAddresses) throws IOException {
        if (firstLocalAddress == null) {
            throw new IllegalArgumentException("Need a local address to bind");
        }

        List<SocketAddress> localAddresses = new ArrayList<>(2);
        localAddresses.add(firstLocalAddress);

        if (otherLocalAddresses != null) {
            localAddresses.addAll(Arrays.asList(otherLocalAddresses));
        }

        bindAddress.addAll(localAddresses);
        /**
         * wait for bind finished
         */
        synchronized (bindLock) {
            this.waitBind();
        }
    }

    @Override
    public void unbind(int port) throws IOException {
        unbind(new InetSocketAddress(port));
    }

    @Override
    public void unbind(SocketAddress firstLocalAddress, SocketAddress... otherLocalAddresses) throws IOException {
        if (firstLocalAddress == null) {
            return;
        }

        List<SocketAddress> localAddresses = new ArrayList<SocketAddress>(2);
        if (bindAddress.contains(firstLocalAddress)) {
            localAddresses.add(firstLocalAddress);
        }

        if (otherLocalAddresses != null) {
            for (SocketAddress address : otherLocalAddresses) {
                if (bindAddress.containsAll(Arrays.asList(otherLocalAddresses))) {
                    localAddresses.add(address);
                }
            }
        }

        unbindAddress.addAll(localAddresses);
    }

    @Override
    public Set<SocketAddress> getBoundAddresses() {
        return this.serverAddressChannel.keySet();
    }

    private void bind0() {
        if (!bindAddress.isEmpty()) {
            for (SocketAddress address : bindAddress) {
                boolean success = false;
                try {
                    SelectableChannel sc = bindInternal(address);
                    success = true;
                    logger.debug("Bind address={}", address);
                } catch (IOException e) {
                    logger.error("Bind address error", e);
                } finally {
                    if (!success) {
                        cancelBind();
                    }
                }
            }
            bindAddress.clear();
            // notify bind end
            synchronized (bindLock) {
                bindEndFlag = true;
                bindLock.notifyAll();
            }
        }
    }

    protected void cancelBind() {
        Iterator<Map.Entry<SocketAddress, SelectableChannel>> it = serverAddressChannel.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<SocketAddress, SelectableChannel> entry = it.next();
            try {
                close(entry.getValue());
            } catch (IOException e) {
                logger.warn("Cancel bind  exception", e);
            } finally {
                it.remove();
            }
        }
    }

    private void unbind0() {
        if (!unbindAddress.isEmpty()) {
            for (SocketAddress address : unbindAddress) {
                try {
                    if (serverAddressChannel.containsKey(address)) {
                        SelectableChannel sc = serverAddressChannel.get(address);
                        close(sc);
                        serverAddressChannel.remove(address);
                    }
                    logger.debug("Unbind address={}", address);
                } catch (IOException e) {
                    bindException = e;
                }
            }
            unbindAddress.clear();

            // notify bind end
            synchronized (bindLock) {
                bindEndFlag = true;
                bindLock.notifyAll();
            }
        }
    }

    @Override
    public void start() throws Throwable {
        selector = Selector.open();
        Thread t = new AcceptThread();
        t.setName("spirit-nio-acceptor");
        t.start();
        this.running = true;
    }

    @Override
    public void shutdown() throws Throwable {
        this.running = false;
        this.shutdown0();
    }

    protected void shutdown0() {
        this.bindAddress.clear();
        this.unbindAddress.clear();

        // close all opened server socket channel
        for (SelectableChannel sc : serverAddressChannel.values()) {
            try {
                logger.debug("Close channel {}", sc);
                close(sc);
            } catch (Exception e) {
                logger.error("Close channel error", e);
            }
        }
    }

    private void close(SelectableChannel sc) throws IOException {
        if (sc != null) {
            sc.close();
        }
    }

    private void waitBind() throws IOException {
        while (!this.bindEndFlag) {
            try {
                bindLock.wait();
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        }

        // reset end flag
        this.bindEndFlag = false;

        if (this.bindException != null) {
            IOException e = bindException;
            this.bindException = null;
            throw e;
        }
    }

    protected abstract void acceptInternal(SelectionKey key) throws IOException;

    protected abstract SelectableChannel bindInternal(SocketAddress address) throws IOException;

    /**
     * Run forever to accept connection and update address binding
     */
    private class AcceptThread extends Thread {
        @Override
        public void run() {
            while (running) {
                try {
                    int select =  selector.select();
                    if(select > 0) {
                        Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                        while (it.hasNext()) {
                            SelectionKey key = it.next();
                            it.remove();
                            acceptInternal(key);
                        }
                    }
                    // update bind port
                    bind0();
                    unbind0();
                } catch (Exception e) {

                }
            }
            try {
                shutdown0();
            } catch (Exception e) {
                logger.error("Failed to shutdown Acceptor[{}]", NioAcceptor.class.getName(), e);
            }
        }
    }
}
