package com.young.spirit.net;

import com.young.spirit.net.exception.IllegalChannelStateException;

import java.util.Queue;

/**
 * Relation of request and response
 */
public interface Channel<T> {
    long getId();

    void close();

    boolean write(T data) throws IllegalChannelStateException;

    boolean isOpen();

    boolean isClosing();

    boolean isPaused();

    void pause() ;

    void resume();

    boolean isClosed();

    Object getAttribute(Object key);

    Object setAttribute(Object key, Object value);

    Object removeAttribute(Object key);

    boolean containsAttribute(Object key);

    Queue<T> getWriteQueue();
}
