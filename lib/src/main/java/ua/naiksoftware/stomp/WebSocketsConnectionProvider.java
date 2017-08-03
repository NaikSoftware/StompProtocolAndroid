package ua.naiksoftware.stomp;

import android.util.Log;

import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import rx.Completable;
import rx.Observable;
import rx.subjects.PublishSubject;

/**
 * Created by naik on 05.05.16.
 */

class WebSocketsConnectionProvider implements ConnectionProvider {

    private static final String TAG = WebSocketsConnectionProvider.class.getSimpleName();

    private final String mUri;
    private final Map<String, String> mConnectHttpHeaders;

    private WebSocketClient mWebSocketClient;
    private boolean haveConnection;
    private TreeMap<String, String> mServerHandshakeHeaders;

    private final PublishSubject<LifecycleEvent> mLifecycleStream;
    private final PublishSubject<String> mMessagesStream;

    /**
     * Support UIR scheme ws://host:port/path
     * @param connectHttpHeaders may be null
     */
    WebSocketsConnectionProvider(String uri, Map<String, String> connectHttpHeaders) {
        mUri = uri;
        mConnectHttpHeaders = connectHttpHeaders != null ? connectHttpHeaders : new HashMap<>();

        mLifecycleStream = PublishSubject.create();
        mMessagesStream = PublishSubject.create();
    }

    @Override
    public Observable<String> messages() {
        createWebSocketConnection();
        return mMessagesStream;
    }

    @Override
    public Completable disconnect() {
        return Completable.fromAction(() -> mWebSocketClient.close());
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
    public Completable send(String stompMessage) {
        return Completable.fromCallable(() -> {
            if (mWebSocketClient == null) {
                throw new IllegalStateException("Not connected yet");
            } else {
                Log.d(TAG, "Send STOMP message: " + stompMessage);
                mWebSocketClient.send(stompMessage);
                return null;
            }
        });
    }

    private void emitLifecycleEvent(LifecycleEvent lifecycleEvent) {
        Log.d(TAG, "Emit lifecycle event: " + lifecycleEvent.getType().name());
        mLifecycleStream.onNext(lifecycleEvent);
    }

    private void emitMessage(String stompMessage) {
        Log.d(TAG, "Emit STOMP message: " + stompMessage);
        mMessagesStream.onNext(stompMessage);
    }

    @Override
    public Observable<LifecycleEvent> getLifecycleReceiver() {
        return mLifecycleStream;
    }
}
