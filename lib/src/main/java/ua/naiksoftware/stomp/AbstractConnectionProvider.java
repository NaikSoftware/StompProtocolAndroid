package ua.naiksoftware.stomp;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.concurrent.TimeUnit;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Completable;
import io.reactivex.CompletableSource;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;

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
        mConnectionStream = BehaviorSubject.create();
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
        CompletableSource ex = Completable.error(new IllegalStateException("Attempted to disconnect when already disconnected"));

        Completable block = mConnectionStream
                .filter(connected -> connected).firstOrError().toCompletable()
                .timeout(1, TimeUnit.SECONDS, ex);

        return Completable
                .fromAction(this::rawDisconnect)
                .startWith(block);
    }

    private Completable initSocket() {
        CompletableSource ex = Completable.error(new IllegalStateException("Attempted to connect when already connected"));

        Completable block = mConnectionStream
                .filter(connected -> !connected).firstOrError().toCompletable()
                .timeout(1, TimeUnit.SECONDS, ex);

        return Completable
                .fromAction(this::createWebSocketConnection)
                .startWith(block);
    }

    // Doesn't do anything at all, only here as a stub
    public Completable setHeartbeat(int ms) {
        return Completable.complete();
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
    public Observable<LifecycleEvent> lifecycle() {
        return mLifecycleStream;
    }

    @Override
    public Flowable<Boolean> connected() {
        return mConnectionStream.toFlowable(BackpressureStrategy.LATEST);
    }
}
