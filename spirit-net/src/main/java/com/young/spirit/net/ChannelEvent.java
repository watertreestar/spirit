package com.young.spirit.net;

public interface ChannelEvent<T> {
    long getId();

    Channel<T> getChannel();

    ChannelEventType getEventType();
}
