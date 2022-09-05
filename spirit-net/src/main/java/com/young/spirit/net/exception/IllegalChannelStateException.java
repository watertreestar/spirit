package com.young.spirit.net.exception;

public class IllegalChannelStateException extends RuntimeException{
    public IllegalChannelStateException() {
    }

    public IllegalChannelStateException(String message) {
        super(message);
    }

    public IllegalChannelStateException(String message, Throwable cause) {
        super(message, cause);
    }

    public IllegalChannelStateException(Throwable cause) {
        super(cause);
    }

    public IllegalChannelStateException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
