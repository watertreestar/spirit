package com.young.spirit.net.nio;

import com.young.spirit.net.ChannelState;
import com.young.spirit.net.SocketChannel;
import com.young.spirit.net.exception.IllegalChannelStateException;
import com.young.spirit.net.nio.config.ChannelConfig;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class NioByteChannel extends SocketChannel<byte[]> {
    protected final AtomicBoolean scheduleFlush = new AtomicBoolean(false);
    /**
     * In order to cache buffer to write nio channel
     */
    @Getter
    private final Queue<ByteBuffer> writeBufferQueue = new LinkedBlockingQueue<>();
    private final NioProcessor processor;
    /**
     * Hold reference after registered on a {@link java.nio.channels.Selector}
     */
    @Setter
    @Getter
    private SelectionKey selectionKey;
    private ChannelConfig channelConfig;

    public NioByteChannel(NioProcessor processor, ChannelConfig channelConfig) {
        this.processor = processor;
        this.channelConfig = channelConfig;
    }

    @Override
    public boolean write(byte[] data) throws IllegalChannelStateException {
        if (isClosing()) {
            throw new IllegalChannelStateException("Channel is closing");
        }
        if (isPaused()) {
            throw new IllegalChannelStateException("Channel is paused");
        }
        if (data == null) {
            return false;
        }
        this.getWriteBufferQueue().add(ByteBuffer.wrap(data));
        processor.flush(this);
        return false;
    }

    @Override
    public Queue<byte[]> getWriteQueue() {
        Queue<byte[]> writeQueue = new LinkedBlockingQueue<>();
        for (ByteBuffer buffer : writeBufferQueue) {
            writeQueue.add(buffer.array());
        }
        return writeQueue;
    }

    boolean isValid() {
        if (isClosing()) {
            return false;
        }
        if (isClosed()) {
            return false;
        }
        return true;
    }

    void setClosing() {
        this.state = ChannelState.CLOSING;
    }

    void setClosed() {
        this.state = ChannelState.CLOSED;
    }

    boolean isReadable() {
        return isOpen() && selectionKey.isValid() && selectionKey.isReadable();
    }

    boolean isWritable() {
        return (isOpen() || isPaused()) && selectionKey.isValid() && selectionKey.isWritable();
    }

    public void unsetScheduleFlush() {
        scheduleFlush.set(false);
    }

    public boolean setScheduleFlush(boolean schedule) {
        if (schedule) {
            return scheduleFlush.compareAndSet(false, schedule);
        }
        scheduleFlush.set(schedule);
        return true;
    }

    public int getReadBufferSize() {
        return channelConfig.getReadBufferSize();
    }

    public void decreaseReadBufferSize(int decreaseTo) {

    }

    public void increaseReadBufferSize(int increaseTo) {

    }

    abstract protected SelectableChannel realChannel();

    /**
     * Write buf to channel
     * @param buf
     * @return
     */
    abstract protected int write(ByteBuffer buf) throws IOException;
}
