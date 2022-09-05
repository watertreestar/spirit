package com.young.spirit.net.nio;

import com.young.commons.allocation.AllocatedBuffer;
import com.young.commons.allocation.BufferAllocator;
import com.young.commons.allocation.DirectBufferAllocator;
import com.young.commons.allocation.SimpleBufferAllocator;
import com.young.commons.lifecycle.LifeCycle;
import com.young.commons.thread.NamedThreadFactory;
import com.young.spirit.net.Processor;
import com.young.spirit.net.nio.config.ProcessorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public abstract class NioProcessor implements Processor<NioByteChannel>, LifeCycle {
    private static final Logger logger = LoggerFactory.getLogger(NioProcessor.class);

    private static final long SELECT_TIMEOUT = 1000L;
    private static final long FLUSH_SPIN_COUNT = 256;

    private final Queue<NioByteChannel> newChannels = new ConcurrentLinkedQueue<>();
    private final Queue<NioByteChannel> flushingChannels = new ConcurrentLinkedQueue<>();
    private final Queue<NioByteChannel> closingChannels = new ConcurrentLinkedQueue<>();
    private final AtomicReference<ProcessThread> processThreadRef = new AtomicReference<>();
    private final AtomicBoolean wakeupCalled = new AtomicBoolean(false);
    private final Executor executor;
    private final ProcessorConfig config;
    private final BufferAllocator allocator;
    private volatile Selector selector;
    private volatile boolean running = false;

    public NioProcessor(ProcessorConfig config) {
        this.config = config;
        if (this.config.isUseDirectBuffer()) {
            allocator = new DirectBufferAllocator();
        } else {
            allocator = new SimpleBufferAllocator();
        }
        executor = Executors.newCachedThreadPool(new NamedThreadFactory("spirit-nio-processor"));
        try {
            selector = Selector.open();
        } catch (IOException e) {
            throw new IllegalStateException("Fail to startup a processor", e);
        }
    }

    @Override
    public void start() {
        ProcessThread pt = processThreadRef.get();

        if (pt == null) {
            pt = new ProcessThread();
            if (processThreadRef.compareAndSet(null, pt)) {
                executor.execute(pt);
            }
        }
    }

    @Override
    public void shutdown() {
        this.running = false;
    }

    /**
     * Close channel
     *
     * @throws IOException
     */
    private void close() throws IOException {
        for (NioByteChannel channel = closingChannels.poll(); channel != null; channel = closingChannels.poll()) {
            if (channel.isClosed()) {
                logger.debug("Skip close because it is already closed, channel={}", channel);
                continue;
            }
            channel.setClosing();
            logger.debug("Closing |channel={}|", channel);
            close(channel);
            channel.setClosed();
            // fire channel closed event
            // fireChannelClosed(channel);
            logger.debug("Closed channel={}", channel);
        }
    }

    private void close(NioByteChannel channel) throws IOException {
        try {
            channel.close();
        } catch (Exception e) {
            logger.warn("Catch close exception and fire it, |channel={}|", channel, e);
            fireChannelThrown(channel, e);
        }
    }


    private int select() throws IOException {
        long t0 = System.currentTimeMillis();
        int selected = selector.select(SELECT_TIMEOUT);
        long t1 = System.currentTimeMillis();
        long delta = (t1 - t0);

        if ((selected == 0) && !wakeupCalled.get() && (delta < 100)) {
            // the select() may have been interrupted because we have had an closed channel.
            if (isBrokenConnection()) {
                logger.debug("Broken connection wakeup");
            } else {
                logger.debug("Create a new selector, |selected={}, delta={}|", selected, delta);
                // it is a workaround method for jdk bug, see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6403933
                registerNewSelector();
            }
            // Set back the flag to false and continue the loop
            wakeupCalled.getAndSet(false);
        }

        return selected;
    }

    private void registerNewSelector() throws IOException {
        synchronized (this) {
            Set<SelectionKey> keys = selector.keys();
            // Open a new selector
            Selector newSelector = Selector.open();
            // Loop on all the registered keys, and register them on the new selector
            for (SelectionKey key : keys) {
                SelectableChannel ch = key.channel();
                // Don't forget to attach the channel, and back !
                NioByteChannel channel = (NioByteChannel) key.attachment();
                ch.register(newSelector, key.interestOps(), channel);
            }
            // Now we can close the old selector and switch it
            selector.close();
            selector = newSelector;
        }
    }

    private boolean isBrokenConnection() {
        boolean broken = false;
        synchronized (selector) {
            Set<SelectionKey> keys = selector.keys();
            for (SelectionKey key : keys) {
                SelectableChannel channel = key.channel();
                if (!((SocketChannel) channel).isConnected()) {
                    // The channel is not connected anymore. Cancel the associated key.
                    key.cancel();
                    broken = true;
                }
            }
        }

        return broken;
    }

    /**
     * Process new channels
     * <p>Register {@code OP_READ} event to selector
     *
     * @throws ClosedChannelException
     */
    private void register() throws ClosedChannelException {
        for (NioByteChannel channel = newChannels.poll(); channel != null; channel = newChannels.poll()) {
            SelectableChannel sc = channel.realChannel();
            // Register READ event with a attachment channel
            SelectionKey key = sc.register(selector, SelectionKey.OP_READ, channel);
            channel.setSelectionKey(key);
            // idleTimer.add(channel);
            // fire channel opened event
            fireChannelOpened(channel);
        }
    }


    /**
     * Process selector event
     */
    private void process() {
        Iterator<SelectionKey> it = selector.selectedKeys().iterator();
        while (it.hasNext()) {
            NioByteChannel channel = (NioByteChannel) it.next().attachment();
            if (channel.isValid()) {
                process0(channel);
            } else {
                logger.debug("Channel is invalid, channel={}", channel);
            }
            it.remove();
        }
    }

    private void process0(NioByteChannel channel) {
        // set last IO time
        channel.setLastIoTime(System.currentTimeMillis());
        // Process reads
        if (channel.isReadable()) {
            logger.debug("Read event process on |channel={}|", channel);
            read(channel);
        }
        // Process writes
        if (channel.isWritable()) {
            logger.debug("Write event process on |channel={}|", channel);
            scheduleFlush(channel);
        }
    }

    private void read(NioByteChannel channel) {
        int bufferSize = channel.getReadBufferSize();
        AllocatedBuffer buf = allocator.allocate(bufferSize);
        logger.debug("Predict buffer |size={}, buffer={}|", bufferSize, buf);

        int readBytes = 0;
        try {
            readBytes = read(channel, buf);
        } catch (Exception e) {
            logger.debug("Catch read exception and fire it, |channel={}|", channel, e);
            // fire exception caught event
            fireChannelThrown(channel, e);
            // if it is IO exception close channel avoid infinite loop.
            if (e instanceof IOException) {
                scheduleClose(channel);
            }
        } finally {
            if (readBytes > 0) {
                buf.getRawBuffer().clear();
            }
        }
    }

    protected abstract int read(NioByteChannel channel, AllocatedBuffer buffer) throws IOException;

    protected int write(NioByteChannel channel, ByteBuffer buf, int maxRemaining) throws IOException {
        if (buf.hasRemaining()) {
            int length = Math.min(buf.remaining(), maxRemaining);
            if (buf.remaining() <= length) {
                return channel.write(buf);
            }
            int oldLimit = buf.limit();
            buf.limit(buf.position() + length);
            try {
                return channel.write(buf);
            } finally {
                buf.limit(oldLimit);
            }
        }
        return 0;
    }

    ;


    private void flush() {
        int c = 0;
        while (!flushingChannels.isEmpty() && c < FLUSH_SPIN_COUNT) {
            NioByteChannel channel = flushingChannels.poll();
            if (channel == null) {
                // Just in case ... It should not happen.
                break;
            }
            // Reset the schedule for flush flag to this channel, as we are flushing it now
            channel.unsetScheduleFlush();
            try {
                if (channel.isClosed() || channel.isClosing()) {
                    logger.debug("Channel is closing or closed, |Channel={}, flushing-channel-size={}|", channel, flushingChannels.size());
                } else {
                    // spin counter avoid infinite loop in this method.
                    c++;
                    flush0(channel);
                }
            } catch (Exception e) {
                logger.debug("Catch flush exception and fire it", e);
                // fire channel thrown event
                fireChannelThrown(channel, e);
                // if it is IO exception close channel avoid infinite loop.
                if (e instanceof IOException) {
                    scheduleClose(channel);
                }
            }
        }
    }

    private void scheduleClose(NioByteChannel channel) {
        if (channel.isClosing() || channel.isClosed()) {
            return;
        }
        closingChannels.add(channel);
    }

    /**
     * Add writable channel to flushing queue
     *
     * @param channel
     */
    private void scheduleFlush(NioByteChannel channel) {
        // Add channel to flushing queue if it's not already in the queue, soon after it will be flushed in the same select loop.
        if (channel.setScheduleFlush(true)) {
            flushingChannels.add(channel);
        }
    }

    /**
     * Fire write event for specific channel
     *
     * @param channel
     * @throws IOException
     */
    private void flush0(NioByteChannel channel) throws IOException {
        logger.debug("Flushing |channel={}|", channel);
        Queue<ByteBuffer> writeQueue = channel.getWriteBufferQueue();

        // First set not be interested to write event
        setInterestedInWrite(channel, false);

        // flush by mode
        if (config.isFairFlush()) {
            fairFlush0(channel, writeQueue);
        } else {
            oneOffFlush0(channel, writeQueue);
        }
        // Write buffer queue is not empty, we re-interest in writing and later flush it.
        if (!writeQueue.isEmpty()) {
            setInterestedInWrite(channel, true);
            scheduleFlush(channel);
        }
    }

    private void oneOffFlush0(NioByteChannel channel, Queue<ByteBuffer> writeQueue) throws IOException {
        ByteBuffer buf = writeQueue.peek();
        if (buf == null) {
            return;
        }
        // fire channel flush event
        fireChannelFlush(channel, buf);

        write(channel, buf, buf.remaining());

        if (buf.hasRemaining()) {
            setInterestedInWrite(channel, true);
            scheduleFlush(channel);
        } else {
            writeQueue.remove();

            // fire channel written event
            fireChannelWritten(channel, buf);
        }
    }

    private void fairFlush0(NioByteChannel channel, Queue<ByteBuffer> writeQueue) throws IOException {
        ByteBuffer buf = null;
        int writtenBytes = 0;
        /**
         * No more than {@code maxWriteBytes} per flush
         */
        int maxWriteBytes = config.getMaxWriteBytes();
        do {
            if (buf == null) {
                buf = writeQueue.peek();
                if (buf == null) {
                    return;
                } else {
                    fireChannelFlush(channel, buf);
                }
            }
            // Max bytes can be flush
            int maxRemaining = maxWriteBytes - writtenBytes;
            int localWrittenBytes = write(channel, buf, maxRemaining);
            writtenBytes += localWrittenBytes;

            // The buffer is all flushed, remove it from write queue
            if (!buf.hasRemaining()) {
                logger.debug("The buffer is all flushed, remove it from write queue");
                writeQueue.remove();
                // fire channel written event
                fireChannelWritten(channel, buf);
                // set buf=null and the next loop if no byte buffer to write then break the loop.
                buf = null;
                continue;
            }

            // 0 byte be written, maybe kernel buffer is full so we re-interest in writing and later flush it.
            if (localWrittenBytes == 0) {
                logger.debug("No byte be written, maybe kernel buffer is full so we re-interest in writing and later flush it, |channel={}|", channel);
                setInterestedInWrite(channel, true);
                scheduleFlush(channel);
                return;
            }

            // The buffer isn't empty(bytes to flush more than max bytes), we re-interest in writing and later flush it.
            if (localWrittenBytes > 0) {
                logger.debug("The buffer isn't empty, bytes to flush more than max bytes, we re-interest in writing and later flush it, |channel={}|", channel);
                setInterestedInWrite(channel, true);
                scheduleFlush(channel);
                return;
            }

            // Wrote too much, so we re-interest in writing and later flush other bytes.
            if (writtenBytes >= maxWriteBytes) {
                logger.debug("Wrote too much, so we re-interest in writing and later flush other bytes, |channel={}|", channel);
                setInterestedInWrite(channel, true);
                scheduleFlush(channel);
                return;
            }
        } while (writtenBytes < maxWriteBytes);
    }


    private void setInterestedInWrite(NioByteChannel channel, boolean isInterested) {
        SelectionKey key = channel.getSelectionKey();

        if (key == null || !key.isValid()) {
            return;
        }

        int oldInterestOps = key.interestOps();
        int newInterestOps = oldInterestOps;
        if (isInterested) {
            newInterestOps |= SelectionKey.OP_WRITE;
        } else {
            newInterestOps &= ~SelectionKey.OP_WRITE;
        }

        if (oldInterestOps != newInterestOps) {
            key.interestOps(newInterestOps);
        }
    }

    /**
     * execute flush
     *
     * @param channel
     */
    public void flush(NioByteChannel channel) {
        if (!this.running) {
            throw new IllegalStateException("The processor is already shutdown!");
        }
        if (channel == null) {
            return;
        }
        if (channel.setScheduleFlush(true)) {
            flushingChannels.add(channel);
        }
        wakeup();
    }


    @Override
    public void registerChannel(NioByteChannel channel) {
        if (!this.running) {
            throw new IllegalStateException("The processor already shutdown!");
        }
        if (channel == null) {
            logger.debug("Add channel is null, return");
            return;
        }
        newChannels.add(channel);
        wakeup();
    }

    private void wakeup() {
        wakeupCalled.getAndSet(true);
        selector.wakeup();
    }

    private void shutdown0() {
    }

    // ----------------------------------fire event------------------

    /**
     * Fire channel add processor event
     *
     * @param channel
     */
    private void fireChannelOpened(NioByteChannel channel) {
    }

    /**
     * Fire channel written event
     *
     * @param channel
     * @param buf
     */
    private void fireChannelWritten(NioByteChannel channel, ByteBuffer buf) {
    }

    /**
     * Fire channel flushed to IO handler
     *
     * @param channel
     * @param buf
     */
    private void fireChannelFlush(NioByteChannel channel, ByteBuffer buf) {
    }

    /**
     * Fire exception event
     *
     * @param channel
     * @param e
     */
    private void fireChannelThrown(NioByteChannel channel, Exception e) {
    }

    private class ProcessThread extends Thread {
        public void run() {
            while (running) {
                try {
                    int selected = select();
                    // flush channels for writable channel
                    flush();
                    // register new channels
                    register();
                    //
                    if (selected > 0) {
                        process();
                    }
                    // close channels
                    close();
                } catch (Exception e) {
                    logger.error("Process exception", e);
                }
            }

            // if shutdown == true, we shutdown the processor
            if (!running) {
                try {
                    shutdown0();
                } catch (Exception e) {
                    logger.error("Processor Shutdown exception", e);
                }
            }
        }
    }

}

