package ua.naiksoftware.stomp;

import android.util.Log;

import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import rx.Observable;
import rx.Subscriber;

/**
 * Created by naik on 05.05.16.
 */
/* package */ class WebSocketsConnectionProvider implements ConnectionProvider {

    private static final String TAG = WebSocketsConnectionProvider.class.getSimpleName();

    private final String mUri;
    private final Map<String, String> mConnectHttpHeaders;

    private final List<Subscriber<? super LifecycleEvent>> mLifecycleSubscribers;
    private final List<Subscriber<? super String>> mMessagesSubscribers;

    private WebSocketClient mWebSocketClient;
    private boolean haveConnection;
    private TreeMap<String, String> mServerHandshakeHeaders;

    /**
     * Support UIR scheme ws://host:port/path
     * @param connectHttpHeaders may be null
     */
    /* package */ WebSocketsConnectionProvider(String uri, Map<String, String> connectHttpHeaders) {
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
            Iterator<Subscriber<? super String>> iterator = mMessagesSubscribers.iterator();
            while (iterator.hasNext()) {
                if (iterator.next().isUnsubscribed()) iterator.remove();
            }

            if (mMessagesSubscribers.size() < 1) {
                Log.d(TAG, "Close web socket connection now in thread " + Thread.currentThread());
                mWebSocketClient.close();
            }
        });

        createWebSocketConnection();
        return observable;
    }

    private void createWebSocketConnection() {
        if (haveConnection)
            throw new IllegalStateException("Already have connection to web socket");

        mWebSocketClient = new WebSocketClient(URI.create(mUri), new Draft_17(), mConnectHttpHeaders, 0) {

            @Override
            public void onWebsocketHandshakeReceivedAsClient(WebSocket conn, ClientHandshake request, ServerHandshake response) throws InvalidDataException {
                Log.d(TAG, "onWebsocketHandshakeReceivedAsClient with response: " + response.getHttpStatus() + " " + response.getHttpStatusMessage());
                mServerHandshakeHeaders = new TreeMap<>();
                Iterator<String> keys = response.iterateHttpFields();
                while (keys.hasNext()) {
                    String key = keys.next();
                    mServerHandshakeHeaders.put(key, response.getFieldValue(key));
                }
            }

            @Override
            public void onOpen(ServerHandshake handshakeData) {
                Log.d(TAG, "onOpen with handshakeData: " + handshakeData.getHttpStatus() + " " + handshakeData.getHttpStatusMessage());
                LifecycleEvent openEvent = new LifecycleEvent(LifecycleEvent.Type.OPENED);
                openEvent.setHandshakeResponseHeaders(mServerHandshakeHeaders);
                emitLifecycleEvent(openEvent);
            }

            @Override
            public void onMessage(String message) {
                Log.d(TAG, "onMessage: " + message);
                emitMessage(message);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                Log.d(TAG, "onClose: code=" + code + " reason=" + reason + " remote=" + remote);
                haveConnection = false;
                emitLifecycleEvent(new LifecycleEvent(LifecycleEvent.Type.CLOSED));
            }

            @Override
            public void onError(Exception ex) {
                Log.e(TAG, "onError", ex);
                emitLifecycleEvent(new LifecycleEvent(LifecycleEvent.Type.ERROR, ex));
            }
        };

        if(mUri.startsWith("wss")) {
            try {
                SSLContext sc = SSLContext.getInstance("TLS");
                sc.init(null, null, null);
                SSLSocketFactory factory = sc.getSocketFactory();
                mWebSocketClient.setSocket(factory.createSocket());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        mWebSocketClient.connect();
        haveConnection = true;
    }

    @Override
    public Observable<Void> send(String stompMessage) {
        return Observable.create(subscriber -> {
            if (mWebSocketClient == null) {
                subscriber.onError(new IllegalStateException("Not connected yet"));
            } else {
                Log.d(TAG, "Send STOMP message: " + stompMessage);
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
            Iterator<Subscriber<? super LifecycleEvent>> iterator = mLifecycleSubscribers.iterator();
            while (iterator.hasNext()) {
                if (iterator.next().isUnsubscribed()) iterator.remove();
            }
        });
    }
}
