package ua.naiksoftware.stomp;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.reactivex.Completable;
import io.reactivex.CompletableEmitter;
import io.reactivex.CompletableOnSubscribe;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

class OkHttpConnectionProvider extends AbstractConnectionProvider {

    private final String mUri;
    @NonNull
    private final Map<String, String> mConnectHttpHeaders;
    private final OkHttpClient mOkHttpClient;
    private final String tag = OkHttpConnectionProvider.class.getSimpleName();

    @Nullable
    private WebSocket openedSocked;

    @Nullable
    private CompletableWebSocketListener currentListener;

    OkHttpConnectionProvider(String uri, @Nullable Map<String, String> connectHttpHeaders, OkHttpClient okHttpClient) {
        super();
        mUri = uri;
        mConnectHttpHeaders = connectHttpHeaders != null ? connectHttpHeaders : new HashMap<>();
        mOkHttpClient = okHttpClient;
    }

    @Override
    public void rawDisconnect() {
        if (openedSocked != null) {
            openedSocked.close(1000, "");
            if (currentListener != null)
                currentListener.awaitCloseBlocking();
        }
    }

    @Override
    void createWebSocketConnection() {
        Request.Builder requestBuilder = new Request.Builder()
                .url(mUri);

        addConnectionHeadersToBuilder(requestBuilder, mConnectHttpHeaders);

        currentListener = new CompletableWebSocketListener();

        openedSocked = mOkHttpClient
                .newWebSocket(requestBuilder.build(), currentListener);
    }

    @Override
    void rawSend(String stompMessage) {
        openedSocked.send(stompMessage);
    }

    @Nullable
    @Override
    Object getSocket() {
        return openedSocked;
    }

    @NonNull
    private TreeMap<String, String> headersAsMap(@NonNull Response response) {
        TreeMap<String, String> headersAsMap = new TreeMap<>();
        Headers headers = response.headers();
        for (String key : headers.names()) {
            headersAsMap.put(key, headers.get(key));
        }
        return headersAsMap;
    }

    private void addConnectionHeadersToBuilder(@NonNull Request.Builder requestBuilder, @NonNull Map<String, String> mConnectHttpHeaders) {
        for (Map.Entry<String, String> headerEntry : mConnectHttpHeaders.entrySet()) {
            requestBuilder.addHeader(headerEntry.getKey(), headerEntry.getValue());
        }
    }

    private class CompletableWebSocketListener extends WebSocketListener implements CompletableOnSubscribe {
        private List<CompletableEmitter> mEmitters = new ArrayList<>();
        private Completable mCompletable = Completable.create(this);

        @Override
        public void onOpen(WebSocket webSocket, @NonNull Response response) {
            LifecycleEvent openEvent = new LifecycleEvent(LifecycleEvent.Type.OPENED);

            TreeMap<String, String> headersAsMap = headersAsMap(response);

            openEvent.setHandshakeResponseHeaders(headersAsMap);
            emitLifecycleEvent(openEvent);
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            if (text.equals("\n"))
                Log.d(tag, "RECEIVED HEARTBEAT");
            else
                emitMessage(text);
        }

        @Override
        public void onMessage(WebSocket webSocket, @NonNull ByteString bytes) {
            emitMessage(bytes.utf8());
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            openedSocked = null;
            emitLifecycleEvent(new LifecycleEvent(LifecycleEvent.Type.CLOSED));
            launchCloseEvent();
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            // in OkHttp, a Failure is equivalent to a JWS-Error *and* a JWS-Close
            emitLifecycleEvent(new LifecycleEvent(LifecycleEvent.Type.ERROR, new Exception(t)));
            openedSocked = null;
            emitLifecycleEvent(new LifecycleEvent(LifecycleEvent.Type.CLOSED));
            launchErrorEvent(t);
        }

        @Override
        public void onClosing(final WebSocket webSocket, final int code, final String reason) {
            webSocket.close(code, reason);
        }

        @Override
        public void subscribe(CompletableEmitter emitter) {
            mEmitters.add(emitter);
        }

        private void awaitCloseBlocking(){
            mCompletable.blockingAwait();
        }

        private void launchCloseEvent(){
            for (CompletableEmitter emitter : mEmitters) {
                if(!emitter.isDisposed())
                    emitter.onComplete();
            }
        }

        private void launchErrorEvent(Throwable t){
            for (CompletableEmitter emitter : mEmitters) {
                if(!emitter.isDisposed())
                    emitter.onError(t);
            }
        }
    }
}
