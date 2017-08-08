package ua.naiksoftware.stomp;

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

class OkHttpConnectionProvider extends AbstractConnectionProvider {

    private final String mUri;
    private final Map<String, String> mConnectHttpHeaders;
    private final OkHttpClient mOkHttpClient;

    private WebSocket openedSocked;

    OkHttpConnectionProvider(String uri, Map<String, String> connectHttpHeaders, OkHttpClient okHttpClient) {
        super();
        mUri = uri;
        mConnectHttpHeaders = connectHttpHeaders != null ? connectHttpHeaders : new HashMap<>();
        mOkHttpClient = okHttpClient;
    }

    @Override
    public Completable disconnect() {
        return Completable.fromAction(() -> openedSocked.close(1000, ""));
    }

    @Override
    void createWebSocketConnection() {

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
    void bareSend(String stompMessage) {
        openedSocked.send(stompMessage);
    }

    @Override
    Object getSocket() {
        return openedSocked;
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
}
