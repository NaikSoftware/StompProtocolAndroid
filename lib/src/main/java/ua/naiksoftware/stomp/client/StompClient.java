package ua.naiksoftware.stomp.client;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;
import ua.naiksoftware.stomp.ConnectionProvider;
import ua.naiksoftware.stomp.LifecycleEvent;
import ua.naiksoftware.stomp.StompHeader;

/**
 * Created by naik on 05.05.16.
 */
public class StompClient {

    public static final String SUPPORTED_VERSIONS = "1.1,1.0";
    public static final String DEFAULT_ACK = "auto";
    private static final String TAG = StompClient.class.getSimpleName();
    private final CompositeDisposable mCompositeDisposable = new CompositeDisposable();
    private final Map<String, SharedEmitter> mEmitters = Collections.synchronizedMap(new HashMap<>());
    private final BehaviorSubject<Boolean> mConnectionSignal = BehaviorSubject.create();
    private final ConnectionProvider mConnectionProvider;
    private final Map<String, String> mTopics = Collections.synchronizedMap(new HashMap<>());
    private boolean mConnected;
    private boolean isConnecting;

    public StompClient(ConnectionProvider connectionProvider) {
        mConnectionProvider = connectionProvider;
    }

    public boolean isConnected() {
        return mConnected;
    }

    public boolean isConnecting() {
        return isConnecting;
    }

    /**
     * Connect without reconnect if connected
     */
    public void connect() {
        connect(null);
    }

    public void connect(boolean reconnect) {
        connect(null, reconnect);
    }

    /**
     * Connect without reconnect if connected
     *
     * @param headers might be null
     */
    public void connect(List<StompHeader> headers) {
        connect(headers, false);
    }

    /**
     * If already connected and reconnect=false - nope
     *
     * @param headers might be null
     */
    public void connect(List<StompHeader> headers, boolean reconnect) {
        if (reconnect) disconnect();
        if (mConnected) return;
        manage(
                mConnectionProvider.getLifecycleReceiver()
                        .subscribe(lifecycleEvent -> {
                            switch (lifecycleEvent.getType()) {
                                case OPENED:
                                    List<StompHeader> headerList = new ArrayList<>();
                                    headerList.add(new StompHeader(StompHeader.VERSION, SUPPORTED_VERSIONS));
                                    if (headers != null) headerList.addAll(headers);
                                    mConnectionProvider.send(
                                            new StompMessage(StompCommand.CONNECT, headerList, null).compile()
                                    ).subscribe();
                                    break;

                                case CLOSED:
                                    setConnected(false);
                                    isConnecting = false;
                                    break;

                                case ERROR:
                                    setConnected(false);
                                    isConnecting = false;
                                    break;
                            }
                        })
        );

        isConnecting = true;
        manage(
                mConnectionProvider.messages()
                        .map(StompMessage::from)
                        .subscribe(stompMessage -> {
                            if (StompCommand.CONNECTED.equals(stompMessage.getStompCommand())) {
                                setConnected(true);
                                isConnecting = false;
                            }
                            callSubscribers(stompMessage);
                        })
        );
    }

    public Completable send(String destination) {
        return send(new StompMessage(
                StompCommand.SEND,
                Collections.singletonList(new StompHeader(StompHeader.DESTINATION, destination)),
                null));
    }

    public Completable send(String destination, String data) {
        return send(new StompMessage(
                StompCommand.SEND,
                Collections.singletonList(new StompHeader(StompHeader.DESTINATION, destination)),
                data));
    }

    public Completable send(StompMessage stompMessage) {
        return mConnectionSignal.filter(connected -> connected).firstOrError().toCompletable()
                .concatWith(mConnectionProvider.send(stompMessage.compile()))
                .doOnSubscribe(this::manage);
    }

    public Flowable<LifecycleEvent> lifecycle() {
        return mConnectionProvider.getLifecycleReceiver()
                .doOnSubscribe(subscription -> manage(Disposables.fromSubscription(subscription)));
    }

    public void disconnect() {
        mCompositeDisposable.clear();
        setConnected(false);
    }

    public Flowable<StompMessage> topic(String destinationPath) {
        return topic(destinationPath, null);
    }

    private void callSubscribers(StompMessage stompMessage) {
        String messageDestination = stompMessage.findHeader(StompHeader.DESTINATION);
        SharedEmitter sharedEmitter = mEmitters.get(messageDestination);
        if (sharedEmitter != null) sharedEmitter.emitter.onNext(stompMessage);
    }

    public Flowable<StompMessage> topic(final String destinationPath, final List<StompHeader> headerList) {
        return Flowable.defer(() -> {
                    synchronized (mEmitters) {
                        SharedEmitter sharedEmitter = mEmitters.get(destinationPath);
                        if (sharedEmitter == null) {
                            PublishSubject<StompMessage> emitter = PublishSubject.create();
                            Flowable<StompMessage> sharedFlowable = emitter.toFlowable(BackpressureStrategy.BUFFER)
                                    .doOnSubscribe(subscription -> {
                                        manage(Disposables.fromSubscription(subscription));
                                        subscribePath(destinationPath, headerList).subscribe();
                                    })
                                    .doFinally(() -> {
                                        synchronized (mEmitters) {
                                            mEmitters.remove(destinationPath);
                                            unsubscribePath(destinationPath).subscribe();
                                        }
                                    })
                                    .share();
                            sharedEmitter = new SharedEmitter(emitter, sharedFlowable);
                            mEmitters.put(destinationPath, sharedEmitter);
                        }
                        return sharedEmitter.sharedFlowable;
                    }
                }
        );

    }

    private Completable subscribePath(String destinationPath, List<StompHeader> headerList) {
        if (destinationPath == null) return Completable.complete();
        String topicId = UUID.randomUUID().toString();
        mTopics.put(destinationPath, topicId);
        List<StompHeader> headers = new ArrayList<>();
        headers.add(new StompHeader(StompHeader.ID, topicId));
        headers.add(new StompHeader(StompHeader.DESTINATION, destinationPath));
        headers.add(new StompHeader(StompHeader.ACK, DEFAULT_ACK));
        if (headerList != null) headers.addAll(headerList);
        return send(new StompMessage(StompCommand.SUBSCRIBE, headers, null));
    }

    private Completable unsubscribePath(String dest) {
        String topicId = mTopics.get(dest);
        Log.d(TAG, "Unsubscribe path: " + dest + " id: " + topicId);

        return send(new StompMessage(
                StompCommand.UNSUBSCRIBE,
                Collections.singletonList(new StompHeader(StompHeader.ID, topicId)),
                null));
    }

    private void setConnected(boolean connected) {
        mConnected = connected;
        mConnectionSignal.onNext(mConnected);
    }

    private void manage(Disposable disposable) {
        mCompositeDisposable.add(disposable);
    }

    private static class SharedEmitter {
        public final PublishSubject<StompMessage> emitter;
        public final Flowable<StompMessage> sharedFlowable;

        private SharedEmitter(PublishSubject<StompMessage> emitter, Flowable<StompMessage> sharedFlowable) {
            this.emitter = emitter;
            this.sharedFlowable = sharedFlowable;
        }
    }
}
