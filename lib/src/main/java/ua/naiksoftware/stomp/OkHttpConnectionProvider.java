package ua.naiksoftware.stomp;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import rx.Completable;
import rx.Observable;
import rx.subjects.PublishSubject;

class OkHttpConnectionProvider implements ConnectionProvider {

    private static final String TAG = WebSocketsConnectionProvider.class.getSimpleName();

    private final String mUri;
    private final Map<String, String> mConnectHttpHeaders;
    private final OkHttpClient mOkHttpClient;

    private final PublishSubject<LifecycleEvent> mLifecycleStream;
    private final PublishSubject<String> mMessagesStream;

    private WebSocket openedSocked;

    OkHttpConnectionProvider(String uri, Map<String, String> connectHttpHeaders, OkHttpClient okHttpClient) {
        mUri = uri;
        mConnectHttpHeaders = connectHttpHeaders != null ? connectHttpHeaders : new HashMap<>();
        mOkHttpClient = okHttpClient;

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
        return Completable.fromAction(() -> openedSocked.close(1000, ""));
    }

    private void createWebSocketConnection() {

        if (openedSocked != null) {
            throw new IllegalStateException("Already have connection to web socket");
        }

        Request.Builder requestBuilder = new Request.Builder()
                .url(mUri);

        addConnectionHeadersToBuilder(requestBuilder, mConnectHttpHeaders);

        openedSocked = mOkHttpClient.newWebSocket(requestBuilder.build(),
                new WebSocketListener() {
                    @Override
                    public void onOpen(WebSocket webSocket, Response response) {
                        LifecycleEvent openEvent = new LifecycleEvent(LifecycleEvent.Type.OPENED);

                        TreeMap<String, String> headersAsMap = headersAsMap(response);

                        openEvent.setHandshakeResponseHeaders(headersAsMap);
                        emitLifecycleEvent(openEvent);
                    }

                    @Override
                    public void onMessage(WebSocket webSocket, String text) {
                        emitMessage(text);
                    }

                    @Override
                    public void onMessage(WebSocket webSocket, ByteString bytes) {
                        emitMessage(bytes.utf8());
                    }

                    @Override
                    public void onClosed(WebSocket webSocket, int code, String reason) {
                        emitLifecycleEvent(new LifecycleEvent(LifecycleEvent.Type.CLOSED));
                        openedSocked = null;
                    }

                    @Override
                    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                        emitLifecycleEvent(new LifecycleEvent(LifecycleEvent.Type.ERROR, new Exception(t)));
                    }
                }

        );
    }

    @Override
    public Completable send(String stompMessage) {
        return Completable.fromCallable(() -> {
            if (openedSocked == null) {
                throw new IllegalStateException("Not connected yet");
            } else {
                Log.d(TAG, "Send STOMP message: " + stompMessage);
                openedSocked.send(stompMessage);
                return null;
            }
        });
    }

    @Override
    public Observable<LifecycleEvent> getLifecycleReceiver() {
        return mLifecycleStream;
    }

    private TreeMap<String, String> headersAsMap(Response response) {
        TreeMap<String, String> headersAsMap = new TreeMap<>();
        Headers headers = response.headers();
        for (String key : headers.names()) {
            headersAsMap.put(key, headers.get(key));
        }
        return headersAsMap;
    }

    private void addConnectionHeadersToBuilder(Request.Builder requestBuilder, Map<String, String> mConnectHttpHeaders) {
        for (Map.Entry<String, String> headerEntry : mConnectHttpHeaders.entrySet()) {
            requestBuilder.addHeader(headerEntry.getKey(), headerEntry.getValue());
        }
    }

    private void emitLifecycleEvent(LifecycleEvent lifecycleEvent) {
        Log.d(TAG, "Emit lifecycle event: " + lifecycleEvent.getType().name());
        mLifecycleStream.onNext(lifecycleEvent);
    }

    private void emitMessage(String stompMessage) {
        Log.d(TAG, "Emit STOMP message: " + stompMessage);
        mMessagesStream.onNext(stompMessage);
    }
}
