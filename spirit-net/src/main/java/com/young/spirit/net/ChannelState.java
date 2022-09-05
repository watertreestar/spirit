package com.young.spirit.net;

public enum ChannelState {

    /** Channel in OPEN state once a channel is created.  */
    OPEN,

    /** Channel in CLOSING state for async close operation, means in closing process. */
    CLOSING,

    /** Channel in ClOSED state, means channel can not be used. */
    CLOSED,

    /** Channel in PAUSED state, means pause accept new data transmit request, but data already in the buffer of channel can be transmitted. */
    PAUSED

}