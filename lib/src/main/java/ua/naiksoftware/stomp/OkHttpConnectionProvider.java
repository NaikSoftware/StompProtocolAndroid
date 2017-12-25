package ua.naiksoftware.stomp;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/* package */ class OkHttpConnectionProvider implements ConnectionProvider {

    private static final String TAG = WebSocketsConnectionProvider.class.getSimpleName();

    private final String mUri;
    private final Map<String, String> mConnectHttpHeaders;
    private final OkHttpClient mOkHttpClient;

    private final List<FlowableEmitter<? super LifecycleEvent>> mLifecycleEmitters;
    private final List<FlowableEmitter<? super String>> mMessagesEmitters;

    private WebSocket openedSocked;


    /* package */ OkHttpConnectionProvider(String uri, Map<String, String> connectHttpHeaders, OkHttpClient okHttpClient) {
        mUri = uri;
        mConnectHttpHeaders = connectHttpHeaders != null ? connectHttpHeaders : new HashMap<>();
        mLifecycleEmitters = Collections.synchronizedList(new ArrayList<>());
        mMessagesEmitters = new ArrayList<>();
        mOkHttpClient = okHttpClient;
    }

    @Override
    public Flowable<String> messages() {
        Flowable<String> flowable = Flowable.<String>create(mMessagesEmitters::add, BackpressureStrategy.BUFFER)
                .doOnCancel(() -> {
                    Iterator<FlowableEmitter<? super String>> iterator = mMessagesEmitters.iterator();
                    while (iterator.hasNext()) {
                        if (iterator.next().isCancelled()) iterator.remove();
                    }

                    if (mMessagesEmitters.size() < 1) {
                        Log.d(TAG, "Close web socket connection now in thread " + Thread.currentThread());
                        openedSocked.close(1000, "");
                        openedSocked = null;
                    }
                });
        createWebSocketConnection();
        return flowable;
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
                    
                    @Override
                    public void onClosing(final WebSocket webSocket, final int code, final String reason) {
                        webSocket.close(code, reason);
                    }
                }

        );
    }

    @Override
    public Flowable<Void> send(String stompMessage) {
        return Flowable.create(subscriber -> {
            if (openedSocked == null) {
                subscriber.onError(new IllegalStateException("Not connected yet"));
            } else {
                Log.d(TAG, "Send STOMP message: " + stompMessage);
                openedSocked.send(stompMessage);
                subscriber.onComplete();
            }
        }, BackpressureStrategy.BUFFER);
    }

    @Override
    public Flowable<LifecycleEvent> getLifecycleReceiver() {
        return Flowable.<LifecycleEvent>create(mLifecycleEmitters::add, BackpressureStrategy.BUFFER)
                .doOnCancel(() -> {
                    synchronized (mLifecycleEmitters) {
                        Iterator<FlowableEmitter<? super LifecycleEvent>> iterator = mLifecycleEmitters.iterator();
                        while (iterator.hasNext()) {
                            if (iterator.next().isCancelled()) iterator.remove();
                        }
                    }
                });
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
        synchronized (mLifecycleEmitters) {
            Log.d(TAG, "Emit lifecycle event: " + lifecycleEvent.getType().name());
            for (FlowableEmitter<? super LifecycleEvent> subscriber : mLifecycleEmitters) {
                subscriber.onNext(lifecycleEvent);
            }
        }
    }

    private void emitMessage(String stompMessage) {
        Log.d(TAG, "Emit STOMP message: " + stompMessage);
        for (FlowableEmitter<? super String> subscriber : mMessagesEmitters) {
            subscriber.onNext(stompMessage);
        }
    }
}
