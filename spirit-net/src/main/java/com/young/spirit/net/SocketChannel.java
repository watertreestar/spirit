package com.young.spirit.net;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@EqualsAndHashCode(of = "id")
@ToString(of = {"id", "state"})
public abstract class SocketChannel<T> implements Channel<T> {
    private static final AtomicLong ID_GENERATOR = new AtomicLong(0);

    protected long id;
    protected Map<Object, Object> attributes = new ConcurrentHashMap<Object, Object>();
    protected volatile ChannelState state = ChannelState.OPEN;
    @Setter
    @Getter
    protected SocketAddress localSocketAddress;
    @Setter
    @Getter
    protected SocketAddress remoteSocketAddress;

    @Getter
    @Setter
    protected volatile long lastIoTime = System.currentTimeMillis();

    public SocketChannel() {
        id = ID_GENERATOR.incrementAndGet();
    }

    public SocketChannel(long id) {
        this.id = id;
    }

    public SocketChannel(long id, SocketAddress localAddress, SocketAddress remoteAddress) {
        this.id = id;
        this.localSocketAddress = localAddress;
        this.remoteSocketAddress = remoteAddress;
    }

    public long getId() {
        return id;
    }

    public boolean isOpen() {
        return state == ChannelState.OPEN;
    }

    public boolean isClosing() {
        return state == ChannelState.CLOSING;
    }

    public boolean isClosed() {
        return state == ChannelState.CLOSED;
    }

    public boolean isPaused() {
        return state == ChannelState.PAUSED;
    }

    public void pause() {
        state = ChannelState.PAUSED;
    }

    public void resume() {
        state = ChannelState.OPEN;
    }

    public void close() {
        state = ChannelState.CLOSED;
    }

    public Object getAttribute(Object key) {
        if (key == null) {
            throw new IllegalArgumentException("key can not be null");
        }

        return attributes.get(key);
    }

    public Object setAttribute(Object key, Object value) {
        if (key == null || value == null) {
            throw new IllegalArgumentException("key & value can not be null");
        }

        return attributes.put(key, value);
    }

    public boolean containsAttribute(Object key) {
        if (key == null) {
            throw new IllegalArgumentException("key can not be null");
        }

        return attributes.containsKey(key);
    }

    public Object removeAttribute(Object key) {
        if (key == null) {
            throw new IllegalArgumentException("key can not be null");
        }

        return attributes.remove(key);
    }

}
