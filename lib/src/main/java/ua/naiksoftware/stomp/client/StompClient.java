package ua.naiksoftware.stomp.client;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import rx.Completable;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.observables.ConnectableObservable;
import rx.subjects.PublishSubject;
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

    /*
    private Subscription mMessagesSubscription;
    private Map<String, Set<Subscriber<? super StompMessage>>> mSubscribers = new HashMap<>();
    */
    private List<Completable> mWaitConnectionCompletables;
    private final ConnectionProvider mConnectionProvider;
    private HashMap<String, String> mTopics;
    private boolean mConnected;
    private boolean isConnecting;

    private PublishSubject<StompMessage> mMessageStream;

    public StompClient(ConnectionProvider connectionProvider) {
        mConnectionProvider = connectionProvider;
        mWaitConnectionCompletables = new CopyOnWriteArrayList<>();
        mMessageStream = PublishSubject.create();
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
     * @param _headers might be null
     */
    public void connect(List<StompHeader> _headers) {
        connect(_headers, false);
    }

    /**
     * If already connected and reconnect=false - nope
     *
     * @param _headers might be null
     */
    public void connect(List<StompHeader> _headers, boolean reconnect) {
        if (reconnect) disconnect();
        if (mConnected) return;
        lifecycle()
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
                            isConnecting = false;
                            break;

                        case ERROR:
                            mConnected = false;
                            isConnecting = false;
                            break;
                    }
                });

        isConnecting = true;
        mConnectionProvider.messages()
                .map(StompMessage::from)
                .doOnNext(this::callSubscribers)
                .filter(msg -> msg.getStompCommand().equals(StompCommand.CONNECTED))
                .subscribe(stompMessage -> {
                    mConnected = true;
                    isConnecting = false;
                    for (Completable completable : mWaitConnectionCompletables) {
                        completable.subscribe();
                    }
                    mWaitConnectionCompletables.clear();
                });
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
        Completable completable = mConnectionProvider.send(stompMessage.compile());
        if (!mConnected) {
            mWaitConnectionCompletables.add(completable);
        }
        return completable;
    }

    /*
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
    */

    private void callSubscribers(StompMessage stompMessage) {
        mMessageStream.onNext(stompMessage);
    }

    public Observable<LifecycleEvent> lifecycle() {
        return mConnectionProvider.getLifecycleReceiver();
    }

    public void disconnect() {
        mConnectionProvider.disconnect().subscribe();
    }

    public Observable<StompMessage> topic(String destinationPath) {
        return topic(destinationPath, null);
    }

    public Observable<StompMessage> topic(String destPath, List<StompHeader> headerList) {
        Observable<StompMessage> ret;

        if (destPath == null)
            ret = Observable.error(new IllegalArgumentException("Topic path cannot be null"));
        else
            ret = mMessageStream
                .filter(msg -> destPath.equals(msg.findHeader(StompHeader.DESTINATION)))
                .doOnSubscribe(() -> subscribePath(destPath, headerList));
        // still need to figure out how to do the unsubscribes reactively... more difficult than it sounds
        return ret;
    }

    /*
    public Observable<StompMessage> topic(String destinationPath, List<StompHeader> headerList) {
        // basically:
        // on SUBSCRIBE, add the observer to the Set in the mSubscribers map that's associated with the specified topic,
        // and send a subscribe message IF WE HAVEN'T ALREADY SUBSCRIBED TO THE TOPIC
        //
        // on UNSUBSCRIBE, remove unsubscribed observers, and remove unobserved topics

        // on observer subscribe...
       return Observable.<StompMessage>create(subscriber -> {
           // get list of other subscribers to topic
           Set<Subscriber<? super StompMessage>> subscribersSet = mSubscribers.get(destinationPath);
           // if there are no other subscribers on topic...
           if (subscribersSet == null) {
               // create new subscriber list,
               subscribersSet = new HashSet<>();
               // and add the list to the map
               mSubscribers.put(destinationPath, subscribersSet);
               // send SUBSCRIBE message and add topic to mTopics
               subscribePath(destinationPath, headerList).subscribe();
           }
           // finally, now that we know that there is a list for this topic, add observer to it
           subscribersSet.add(subscriber);

       }).doOnUnsubscribe(() -> {
           // on unsubscribe...
           Iterator<String> mapIterator = mSubscribers.keySet().iterator();
           // for each topic in the map,
           while (mapIterator.hasNext()) {
               // get topic path
               String destinationUrl = mapIterator.next();
               // get observers subscribed to this topic
               Set<Subscriber<? super StompMessage>> set = mSubscribers.get(destinationUrl);
               Iterator<Subscriber<? super StompMessage>> setIterator = set.iterator();
               // for each observer subscribed to this topic,
               while (setIterator.hasNext()) {
                   Subscriber<? super StompMessage> subscriber = setIterator.next();
                   // if observer is no longer subscribed,
                   if (subscriber.isUnsubscribed()) {
                       // remove it from the set
                       setIterator.remove();
                       // if there are no observers subscribed to this topic anymore...
                       if (set.size() < 1) {
                           // remote the set from the map
                           mapIterator.remove();
                           // send UNSUBSCRIBE message
                           unsubscribePath(destinationUrl).subscribe();
                       }
                   }
               }
           }
       });
   }
   */

    private Completable subscribePath(String destinationPath, List<StompHeader> headerList) {
        String topicId = UUID.randomUUID().toString();

        if (mTopics == null) mTopics = new HashMap<>();

        // Only continue if we don't already have a subscription to the topic
        if (mTopics.containsKey(destinationPath))
            return Completable.complete();

        mTopics.put(destinationPath, topicId);
        List<StompHeader> headers = new ArrayList<>();
        headers.add(new StompHeader(StompHeader.ID, topicId));
        headers.add(new StompHeader(StompHeader.DESTINATION, destinationPath));
        headers.add(new StompHeader(StompHeader.ACK, DEFAULT_ACK));
        if (headerList != null) headers.addAll(headerList);
        return send(new StompMessage(StompCommand.SUBSCRIBE,
                headers, null));
    }


    private Completable unsubscribePath(String dest) {
        String topicId = mTopics.get(dest);
        Log.d(TAG, "Unsubscribe path: " + dest + " id: " + topicId);

        return send(new StompMessage(StompCommand.UNSUBSCRIBE,
                Collections.singletonList(new StompHeader(StompHeader.ID, topicId)), null));
    }

    public boolean isConnected() {
        return mConnected;
    }

    public boolean isConnecting() {
        return isConnecting;
    }
}
