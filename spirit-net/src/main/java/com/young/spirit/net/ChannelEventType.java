package com.young.spirit.net;

public enum ChannelEventType {
    /**
     * When channel has been opened, fire this event
     */
    CHANNEL_OPENED,

    /**
     * When channel has been closed, fire this event
     */
    CHANNEL_CLOSED,

    /**
     * When channel has read some data, fire this event
     */
    CHANNEL_READ,

    /**
     * When channel would flush out data in it, fire this event
     */
    CHANNEL_FLUSH,

    /**
     * When channel has written some data, fire this event
     */
    CHANNEL_WRITTEN,

    /**
     * When channel has no data transmit for a while, fire this event
     */
    CHANNEL_IDLE,

    /**
     * When channel operation throw exception, fire this event
     */
    CHANNEL_THROWN

}

