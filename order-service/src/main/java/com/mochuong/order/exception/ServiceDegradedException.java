package com.mochuong.order.exception;

public class ServiceDegradedException extends RuntimeException {
    public ServiceDegradedException(String message, Throwable cause) {
        super(message, cause);
    }

    public ServiceDegradedException(String message) {
        super(message);
    }
}
