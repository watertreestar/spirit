package com.young.spirit.net;

public abstract class AbstractChannelEvent<T> implements ChannelEvent<T>{
    protected ChannelEventType eventType;

    public AbstractChannelEvent(ChannelEventType eventType) {
        this.eventType = eventType;
    }

    @Override
    public ChannelEventType getEventType() {
        return eventType;
    }
}
