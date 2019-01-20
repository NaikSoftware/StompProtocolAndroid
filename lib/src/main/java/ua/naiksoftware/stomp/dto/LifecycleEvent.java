package ua.naiksoftware.stomp.dto;


import java.util.TreeMap;

/**
 * Created by naik on 05.05.16.
 */
public class LifecycleEvent {

    public enum Type {
        OPENED, CLOSED, ERROR, FAILED_SERVER_HEARTBEAT
    }

    private final Type mType;

    //Nullable
    private Exception mException;

    //Nullable
    private String mMessage;

    private TreeMap<String, String> handshakeResponseHeaders = new TreeMap<>();

    public LifecycleEvent(Type type) {
        mType = type;
    }

    public LifecycleEvent(Type type, Exception exception) {
        mType = type;
        mException = exception;
    }

    public LifecycleEvent(Type type, String message) {
        mType = type;
        mMessage = message;
    }

    public Type getType() {
        return mType;
    }

    public Exception getException() {
        return mException;
    }

    public String getMessage() {
        return mMessage;
    }

    public void setHandshakeResponseHeaders(TreeMap<String, String> handshakeResponseHeaders) {
        this.handshakeResponseHeaders = handshakeResponseHeaders;
    }

    public TreeMap<String, String> getHandshakeResponseHeaders() {
        return handshakeResponseHeaders;
    }
}
