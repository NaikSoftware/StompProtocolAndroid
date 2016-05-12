package ua.naiksoftware.stomp;

import android.util.Log;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import rx.Observable;
import rx.Subscriber;

/**
 * Created by naik on 05.05.16.
 */
public class WebSocketsConnectionProvider implements ConnectionProvider {

    private static final String TAG = WebSocketsConnectionProvider.class.getSimpleName();

    private final String mUri;
    private final Map<String, String> mConnectHttpHeaders;
    private WebSocketClient mWebSocketClient;
    private List<Subscriber<? super LifecycleEvent>> mLifecycleSubscribers;
    private List<Subscriber<? super String>> mMessagesSubscribers;
    private boolean haveConnection;

    /**
     * Support UIR scheme ws://host:port/path
     * @param connectHttpHeaders may be null
     */
    public WebSocketsConnectionProvider(String uri, Map<String, String> connectHttpHeaders) {
        mUri = uri;
        mConnectHttpHeaders = connectHttpHeaders != null ? connectHttpHeaders : new HashMap<>();
        mLifecycleSubscribers = new ArrayList<>();
        mMessagesSubscribers = new ArrayList<>();
    }

    @Override
    public Observable<String> messages() {
        Observable<String> observable = Observable.<String>create(subscriber -> {
            mMessagesSubscribers.add(subscriber);

        }).doOnUnsubscribe(() -> {
            for (Subscriber<? super String> subscriber : mMessagesSubscribers) {
                if (subscriber.isUnsubscribed()) mMessagesSubscribers.remove(subscriber);
            }

            if (mMessagesSubscribers.size() < 1) mWebSocketClient.close();
        });

        createWebSocketConnection();
        return observable;
    }

    private void createWebSocketConnection() {
        if (haveConnection)
            throw new IllegalStateException("Already have connection to web socket");

        mWebSocketClient = new WebSocketClient(URI.create(mUri), new Draft_17(), mConnectHttpHeaders, 0) {
            @Override
            public void onOpen(ServerHandshake handshakeData) {
                emitLifecycleEvent(new LifecycleEvent(LifecycleEvent.Type.OPENED));
            }

            @Override
            public void onMessage(String message) {
                emitMessage(message);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                emitLifecycleEvent(new LifecycleEvent(LifecycleEvent.Type.CLOSED));
            }

            @Override
            public void onError(Exception ex) {
                ex.printStackTrace();
                emitLifecycleEvent(new LifecycleEvent(LifecycleEvent.Type.ERROR, ex));
            }
        };
        mWebSocketClient.connect();
        haveConnection = true;
    }

    @Override
    public Observable<Void> send(String stompMessage) {
        return Observable.create(subscriber -> {
            if (mWebSocketClient == null) {
                subscriber.onError(new IllegalStateException("Not connected yet"));
            } else {
                mWebSocketClient.send(stompMessage);
                subscriber.onCompleted();
            }
        });
    }

    private void emitLifecycleEvent(LifecycleEvent lifecycleEvent) {
        Log.d(TAG, "Emit lifecycle event: " + lifecycleEvent.getType().name());
        for (Subscriber<? super LifecycleEvent> subscriber : mLifecycleSubscribers) {
            subscriber.onNext(lifecycleEvent);
        }
    }

    private void emitMessage(String stompMessage) {
        Log.d(TAG, "Emit STOMP message: " + stompMessage);
        for (Subscriber<? super String> subscriber : mMessagesSubscribers) {
            subscriber.onNext(stompMessage);
        }
    }

    @Override
    public Observable<LifecycleEvent> getLifecycleReceiver() {
        return Observable.<LifecycleEvent>create(subscriber -> {
            mLifecycleSubscribers.add(subscriber);

        }).doOnUnsubscribe(() -> {
            for (Subscriber<? super LifecycleEvent> subscriber : mLifecycleSubscribers) {
                if (subscriber.isUnsubscribed()) mLifecycleSubscribers.remove(subscriber);
            }
        });
    }
}
