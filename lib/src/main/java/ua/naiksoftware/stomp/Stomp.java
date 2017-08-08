package ua.naiksoftware.stomp;

import android.support.annotation.Nullable;

import java.util.Map;

import okhttp3.OkHttpClient;
import ua.naiksoftware.stomp.client.StompClient;

/**
 * Supported overlays:
 *  - org.java_websocket.WebSocket ('org.java-websocket:Java-WebSocket:1.3.2')
 *  - okhttp3.WebSocket ('com.squareup.okhttp3:okhttp:3.8.1')
 *
 *  You can add own relay, just implement ConnectionProvider for you stomp transport,
 *  such as web socket.
 *
 * Created by naik on 05.05.16.
 */
public class Stomp {

    public static StompClient over(Transport transport, String uri) {
        return over(transport, uri, null, null);
    }

    /**
     *
     * @param transport transport method
     * @param uri URI to connect
     * @param connectHttpHeaders HTTP headers, will be passed with handshake query, may be null
     * @return StompClient for receiving and sending messages. Call #StompClient.connect
     */
    public static StompClient over(Transport transport, String uri, Map<String, String> connectHttpHeaders) {
        return over(transport, uri, connectHttpHeaders, null);
    }

    /**
     * {@code webSocketClient} can accept the following type of clients:
     * <ul>
     *     <li>{@code org.java_websocket.WebSocket}: cannot accept an existing client</li>
     *     <li>{@code okhttp3.WebSocket}: can accept a non-null instance of {@code okhttp3.OkHttpClient}</li>
     * </ul>
     * @param transport transport method
     * @param uri URI to connect
     * @param connectHttpHeaders HTTP headers, will be passed with handshake query, may be null
     * @param okHttpClient Existing client that will be used to open the WebSocket connection, may be null to use default client
     * @return StompClient for receiving and sending messages. Call #StompClient.connect
     */
    public static StompClient over(Transport transport, String uri, @Nullable Map<String, String> connectHttpHeaders, @Nullable OkHttpClient okHttpClient) {
        if (transport == Transport.JWS) {
            if (okHttpClient != null) {
                throw new IllegalArgumentException("You cannot pass a webSocketClient with 'org.java_websocket.WebSocket'. use null instead.");
            }
            return createStompClient(new WebSocketsConnectionProvider(uri, connectHttpHeaders));
        }

        if (transport == Transport.OKHTTP) {
            return createStompClient(new OkHttpConnectionProvider(uri, connectHttpHeaders, (okHttpClient == null) ? new OkHttpClient() : okHttpClient));
        }

        throw new IllegalArgumentException("Transport type not supported: " + transport.toString());
    }

    private static StompClient createStompClient(ConnectionProvider connectionProvider) {
        return new StompClient(connectionProvider);
    }

    public enum Transport {
        OKHTTP, JWS
    }
}
