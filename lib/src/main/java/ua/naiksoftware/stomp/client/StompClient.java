package ua.naiksoftware.stomp.client;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.observables.ConnectableObservable;
import ua.naiksoftware.stomp.ConnectionProvider;
import ua.naiksoftware.stomp.LifecycleEvent;
import ua.naiksoftware.stomp.StompHeader;

/**
 * Created by naik on 05.05.16.
 */
public class StompClient {

    private static final String TAG = StompClient.class.getSimpleName();

    public static final String SUPPORTED_VERSIONS = "1.1,1.0";
    public static final String DEFAULT_ACK = "auto";

    private Subscription mMessagesSubscription;
    private Map<String, Set<Subscriber<? super StompMessage>>> mSubscribers = new HashMap<>();
    private List<ConnectableObservable<Void>> mWaitConnectionObservables;
    private final ConnectionProvider mConnectionProvider;
    private HashMap<String, String> mTopics;
    private boolean mConnected;

    public StompClient(ConnectionProvider connectionProvider) {
        mConnectionProvider = connectionProvider;
        mWaitConnectionObservables = new ArrayList<>();
    }

    public void connect() {
        connect(null);
    }

    public void connect(List<StompHeader> _headers) {
        mConnectionProvider.getLifecycleReceiver()
                .subscribe(lifecycleEvent -> {
                    switch (lifecycleEvent.getType()) {
                        case OPENED:
                            List<StompHeader> headers = new ArrayList<>();
                            headers.add(new StompHeader(StompHeader.VERSION, SUPPORTED_VERSIONS));
                            if (_headers != null) headers.addAll(_headers);
                            mConnectionProvider.send(new StompMessage(StompCommand.CONNECT, headers, null).compile())
                                    .subscribe();
                            break;

                        case CLOSED:
                            mConnected = false;
                            break;
                    }
                });

        mMessagesSubscription = mConnectionProvider.messages()
                .map(StompMessage::from)
                .subscribe(stompMessage -> {
                    if (stompMessage.getStompCommand().equals(StompCommand.CONNECTED)) {
                        mConnected = true;
                        for (ConnectableObservable<Void> observable : mWaitConnectionObservables) {
                            observable.connect();
                        }
                        mWaitConnectionObservables.clear();
                    }
                    callSubscribers(stompMessage);
                });
    }

    public Observable<Void> send(String destination, String data) {
        return send(new StompMessage(
                StompCommand.SEND,
                Collections.singletonList(new StompHeader(StompHeader.DESTINATION, destination)),
                data));
    }

    public Observable<Void> send(StompMessage stompMessage) {
        Observable<Void> observable = mConnectionProvider.send(stompMessage.compile());
        if (!mConnected) {
            ConnectableObservable<Void> deffered = observable.publish();
            mWaitConnectionObservables.add(deffered);
            return deffered;
        } else {
            return observable;
        }
    }

    private void callSubscribers(StompMessage stompMessage) {
        String messageDestination = stompMessage.findHeader(StompHeader.DESTINATION);
        for (String dest : mSubscribers.keySet()) {
            if (dest.equals(messageDestination)) {
                for (Subscriber<? super StompMessage> subscriber : mSubscribers.get(dest)) {
                    subscriber.onNext(stompMessage);
                }
                return;
            }
        }
    }

    public Observable<LifecycleEvent> lifecycle() {
        return mConnectionProvider.getLifecycleReceiver();
    }

    public void disconnect() {
        if (mMessagesSubscription != null) mMessagesSubscription.unsubscribe();
    }

    public Observable<StompMessage> topic(String destinationPath) {
        return Observable.<StompMessage>create(subscriber -> {

            Set<Subscriber<? super StompMessage>> subscribersSet = mSubscribers.get(destinationPath);
            if (subscribersSet == null) {
                subscribersSet = new HashSet<>();
                mSubscribers.put(destinationPath, subscribersSet);
                subscribePath(destinationPath);
            }
            subscribersSet.add(subscriber);

        }).doOnUnsubscribe(() -> {
            for (String dest : mSubscribers.keySet()) {
                Set<Subscriber<? super StompMessage>> set = mSubscribers.get(dest);
                for (Subscriber<? super StompMessage> subscriber : set) {
                    if (subscriber.isUnsubscribed()) {
                        set.remove(subscriber);
                        if (set.size() < 1) {
                            mSubscribers.remove(dest);
                            unsubscribePath(dest);
                        }
                    }
                }
            }
        });
    }

    private void subscribePath(String destinationPath) {
        if (destinationPath == null) return;
        String topicId = UUID.randomUUID().toString();
        Log.d(TAG, "Subscribe path: " + destinationPath + " id: " + topicId);

        if (mTopics == null) mTopics = new HashMap<>();
        mTopics.put(destinationPath, topicId);
        send(new StompMessage(StompCommand.SUBSCRIBE,
                Arrays.asList(
                        new StompHeader(StompHeader.ID, topicId),
                        new StompHeader(StompHeader.DESTINATION, destinationPath),
                        new StompHeader(StompHeader.ACK, DEFAULT_ACK)), null));
    }


    private void unsubscribePath(String dest) {
        String topicId = mTopics.get(dest);
        Log.d(TAG, "Unsubscribe path: " + dest + " id: " + topicId);

        send(new StompMessage(StompCommand.UNSUBSCRIBE,
                Collections.singletonList(new StompHeader(StompHeader.ID, topicId)), null));
    }
}
