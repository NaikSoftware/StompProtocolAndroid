package ua.naiksoftware.stomp;

import android.support.annotation.Nullable;

/**
 * Created by naik on 05.05.16.
 */
public class LifecycleEvent {

    public enum Type {
        OPENED, CLOSED, ERROR
    }

    private final Type mType;

    @Nullable
    private Exception mException;

    @Nullable
    private String mMessage;

    public LifecycleEvent(Type type) {
        mType = type;
    }

    public LifecycleEvent(Type type, @Nullable Exception exception) {
        mType = type;
        mException = exception;
    }

    public LifecycleEvent(Type type, @Nullable String message) {
        mType = type;
        mMessage = message;
    }

    public Type getType() {
        return mType;
    }

    @Nullable
    public Exception getException() {
        return mException;
    }

    @Nullable
    public String getMessage() {
        return mMessage;
    }
}
