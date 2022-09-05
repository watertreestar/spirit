package com.young.spirit.net;

/**
 * Handler for I/O event
 */
public interface Handler {
    /**
     * Invoked when channel opened.
     *
     * @param channel
     */
    void channelOpened(Channel<byte[]> channel);

    /**
     * Invoked when channel closed.
     *
     * @param channel
     */
    void channelClosed(Channel<byte[]> channel);


    /**
     * Invoked when channel is idle, idle means there is no data transmission (read or write).
     *
     * @param channel
     */
    void channelIdle(Channel<byte[]> channel);

    /**
     * Invoked when channel has read some bytes.
     *
     * @param channel
     * @param bytes
     */
    void channelRead(Channel<byte[]> channel, byte[] bytes);

    /**
     * Invoked when channel would flush out some bytes.
     *
     * @param channel
     * @param bytes
     */
    void channelFlush(Channel<byte[]> channel, byte[] bytes);

    /**
     * Invoked when channel has written some bytes.
     *
     * @param channel
     * @param bytes
     */
    void channelWritten(Channel<byte[]> channel, byte[] bytes);

    /**
     * Invoked when any exception is thrown.
     * If <code>cause</code> is an instance of {@link java.io.IOException} channel should be closed.
     *
     * @param channel
     * @param cause
     */
    void channelThrown(Channel<byte[]> channel, Exception cause);
}
