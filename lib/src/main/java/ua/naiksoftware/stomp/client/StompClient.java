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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.disposables.Disposable;
import io.reactivex.flowables.ConnectableFlowable;
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

    private Disposable mMessagesDisposable;
    private Disposable mLifecycleDisposable;
    private Map<String, Set<FlowableEmitter<? super StompMessage>>> mEmitters = Collections.synchronizedMap(new HashMap<>());
    private List<ConnectableFlowable<Void>> mWaitConnectionFlowables;
    private final ConnectionProvider mConnectionProvider;
    private HashMap<String, String> mTopics;
    private boolean mConnected;
    private boolean isConnecting;

    public StompClient(ConnectionProvider connectionProvider) {
        mConnectionProvider = connectionProvider;
        mWaitConnectionFlowables = new CopyOnWriteArrayList<>();
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
        mLifecycleDisposable = mConnectionProvider.getLifecycleReceiver()
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
        mMessagesDisposable = mConnectionProvider.messages()
                .map(StompMessage::from)
                .subscribe(stompMessage -> {
                    if (stompMessage.getStompCommand().equals(StompCommand.CONNECTED)) {
                        mConnected = true;
                        isConnecting = false;
                        for (ConnectableFlowable<Void> flowable : mWaitConnectionFlowables) {
                            flowable.connect();
                        }
                        mWaitConnectionFlowables.clear();
                    }
                    callSubscribers(stompMessage);
                });
    }

    public Flowable<Void> send(String destination) {
        return send(new StompMessage(
                StompCommand.SEND,
                Collections.singletonList(new StompHeader(StompHeader.DESTINATION, destination)),
                null));
    }

    public Flowable<Void> send(String destination, String data) {
        return send(new StompMessage(
                StompCommand.SEND,
                Collections.singletonList(new StompHeader(StompHeader.DESTINATION, destination)),
                data));
    }

    public Flowable<Void> send(StompMessage stompMessage) {
        Flowable<Void> flowable = mConnectionProvider.send(stompMessage.compile());
        if (!mConnected) {
            ConnectableFlowable<Void> deferred = flowable.publish();
            mWaitConnectionFlowables.add(deferred);
            return deferred;
        } else {
            return flowable;
        }
    }

    private void callSubscribers(StompMessage stompMessage) {
        String messageDestination = stompMessage.findHeader(StompHeader.DESTINATION);
        synchronized (mEmitters) {
            for (String dest : mEmitters.keySet()) {
                if (dest.equals(messageDestination)) {
                    for (FlowableEmitter<? super StompMessage> subscriber : mEmitters.get(dest)) {
                        subscriber.onNext(stompMessage);
                    }
                    return;
                }
            }
        }
    }

    public Flowable<LifecycleEvent> lifecycle() {
        return mConnectionProvider.getLifecycleReceiver();
    }

    public void disconnect() {
        if (mMessagesDisposable != null) mMessagesDisposable.dispose();
        if (mLifecycleDisposable != null) mLifecycleDisposable.dispose();
        mConnected = false;
    }

    public Flowable<StompMessage> topic(String destinationPath) {
        return topic(destinationPath, null);
    }

    public Flowable<StompMessage> topic(String destinationPath, List<StompHeader> headerList) {
       return Flowable.<StompMessage>create(emitter -> {
           synchronized (mEmitters) {
               Set<FlowableEmitter<? super StompMessage>> emittersSet = mEmitters.get(destinationPath);
               if (emittersSet == null) {
                   emittersSet = new HashSet<>();
                   mEmitters.put(destinationPath, emittersSet);
                   subscribePath(destinationPath, headerList).subscribe();
               }
               emittersSet.add(emitter);
           }
       }, BackpressureStrategy.BUFFER)
           .doOnCancel(() -> {
               synchronized (mEmitters) {
                   Iterator<String> mapIterator = mEmitters.keySet().iterator();
                   while (mapIterator.hasNext()) {
                       String destinationUrl = mapIterator.next();
                       Set<FlowableEmitter<? super StompMessage>> set = mEmitters.get(destinationUrl);
                       Iterator<FlowableEmitter<? super StompMessage>> setIterator = set.iterator();
                       while (setIterator.hasNext()) {
                           FlowableEmitter<? super StompMessage> subscriber = setIterator.next();
                           if (subscriber.isCancelled()) {
                               setIterator.remove();
                               if (set.size() < 1) {
                                   mapIterator.remove();
                                   unsubscribePath(destinationUrl).subscribe();
                               }
                           }
                       }
                   }
               }
           });
   }

    private Flowable<Void> subscribePath(String destinationPath, List<StompHeader> headerList) {
          if (destinationPath == null) return Flowable.empty();
          String topicId = UUID.randomUUID().toString();

          if (mTopics == null) mTopics = new HashMap<>();
          mTopics.put(destinationPath, topicId);
          List<StompHeader> headers = new ArrayList<>();
          headers.add(new StompHeader(StompHeader.ID, topicId));
          headers.add(new StompHeader(StompHeader.DESTINATION, destinationPath));
          headers.add(new StompHeader(StompHeader.ACK, DEFAULT_ACK));
          if (headerList != null) headers.addAll(headerList);
          return send(new StompMessage(StompCommand.SUBSCRIBE,
                  headers, null));
      }


    private Flowable<Void> unsubscribePath(String dest) {
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
