package ua.naiksoftware.stomp;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.concurrent.TimeUnit;

import rx.Completable;
import rx.Observable;
import rx.subjects.BehaviorSubject;
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
    final BehaviorSubject<Boolean> mConnectionStream;

    AbstractConnectionProvider() {
        mLifecycleStream = PublishSubject.create();
        mMessagesStream = PublishSubject.create();
        mConnectionStream = BehaviorSubject.create(false);
    }

    @NonNull
    @Override
    public Observable<String> messages() {
        return mMessagesStream.startWith(initSocket().toObservable());
    }

    /**
     * Simply close socket.
     * <p>
     * For example:
     * <pre>
     * webSocket.close();
     * </pre>
     */
    abstract void rawDisconnect();

    @Override
    public Completable disconnect() {
        Observable<Boolean> ex = Observable.error(new IllegalStateException("Attempted to disconnect when already disconnected"));

        Completable block = mConnectionStream
                .first(isConnected -> isConnected)
                .timeout(1, TimeUnit.SECONDS, ex)
                .toCompletable();

        return Completable
                .fromAction(this::rawDisconnect)
                .startWith(block);
    }

    private Completable initSocket() {
        Observable<Boolean> ex = Observable.error(new IllegalStateException("Attempted to connect when already connected"));

        Completable block = mConnectionStream
                .first(isConnected -> !isConnected)
                .timeout(1, TimeUnit.SECONDS, ex)
                .toCompletable();

        return Completable
                .fromAction(this::createWebSocketConnection)
                .startWith(block);
    }

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
                rawSend(stompMessage);
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
    abstract void rawSend(String stompMessage);

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
        if (lifecycleEvent.getType().equals(LifecycleEvent.Type.CLOSED))
            mConnectionStream.onNext(false);
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
