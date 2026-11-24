package com.momentsync.interaction;

public class InteractionException extends RuntimeException {
    private final int status;
    private final int code;
    public InteractionException(int status, int code, String message) {
        super(message); this.status = status; this.code = code;
    }
    public int status() { return status; }
    public int code() { return code; }
    public static InteractionException invalid(String message) {
        return new InteractionException(400, 40001, message);
    }
    public static InteractionException unavailable() {
        return new InteractionException(404, 40401, "印记不存在或已不可见");
    }
}
