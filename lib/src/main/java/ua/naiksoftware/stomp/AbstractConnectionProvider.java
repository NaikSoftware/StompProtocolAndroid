package ua.naiksoftware.stomp;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import rx.Completable;
import rx.Observable;
import rx.subjects.PublishSubject;

/**
 * Created by forresthopkinsa on 8/8/2017.
 * <p>
 * Created because there was a lot of shared code between JWS and OkHttp connection providers.
 */

abstract class AbstractConnectionProvider implements ConnectionProvider {

    private static final String TAG = AbstractConnectionProvider.class.getSimpleName();

    @NonNull
    private final PublishSubject<LifecycleEvent> mLifecycleStream;
    @NonNull
    private final PublishSubject<String> mMessagesStream;

    AbstractConnectionProvider() {
        mLifecycleStream = PublishSubject.create();
        mMessagesStream = PublishSubject.create();
    }

    @NonNull
    @Override
    public Observable<String> messages() {
        createWebSocketConnection();
        return mMessagesStream;
    }

    /**
     * Completable to close socket.
     * <p>
     * For example:
     * <pre>
     * return Completable.fromAction(() -> webSocket.close());
     * </pre>
     */
    @Override
    public abstract Completable disconnect();

    /**
     * Most important method: connects to websocket and notifies program of messages.
     * <p>
     * See implementations in OkHttpConnectionProvider and WebSocketsConnectionProvider.
     */
    abstract void createWebSocketConnection();

    @NonNull
    @Override
    public Completable send(String stompMessage) {
        return Completable.fromCallable(() -> {
            if (getSocket() == null) {
                throw new IllegalStateException("Not connected yet");
            } else {
                Log.d(TAG, "Send STOMP message: " + stompMessage);
                bareSend(stompMessage);
                return null;
            }
        });
    }

    /**
     * Just a simple message send.
     * <p>
     * For example:
     * <pre>
     * webSocket.send(stompMessage);
     * </pre>
     *
     * @param stompMessage message to send
     */
    abstract void bareSend(String stompMessage);

    /**
     * Get socket object.
     * Used for null checking; this object is expected to be null when the connection is not yet established.
     * <p>
     * For example:
     * <pre>
     * return webSocket;
     * </pre>
     */
    @Nullable
    abstract Object getSocket();

    void emitLifecycleEvent(@NonNull LifecycleEvent lifecycleEvent) {
        Log.d(TAG, "Emit lifecycle event: " + lifecycleEvent.getType().name());
        mLifecycleStream.onNext(lifecycleEvent);
    }

    void emitMessage(String stompMessage) {
        Log.d(TAG, "Emit STOMP message: " + stompMessage);
        mMessagesStream.onNext(stompMessage);
    }

    @NonNull
    @Override
    public Observable<LifecycleEvent> getLifecycleReceiver() {
        return mLifecycleStream;
    }
}
