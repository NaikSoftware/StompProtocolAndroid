package ua.naiksoftware.stomp;

import org.java_websocket.WebSocket;

import java.util.Map;

import ua.naiksoftware.stomp.client.StompClient;

/**
 * Supported overlays:
 *  - org.java_websocket.WebSocket ('org.java-websocket:Java-WebSocket:1.3.0')
 *
 *  You can add own relay, just implement ConnectionProvider for you stomp transport,
 *  such as web socket.
 *
 * Created by naik on 05.05.16.
 */
public class Stomp {

    public static StompClient over(Class clazz, String uri) {
        return over(clazz, uri, null);
    }

    /**
     *
     * @param clazz class for using as transport
     * @param uri URI to connect
     * @param connectHttpHeaders HTTP headers, will be passed with handshake query, may be null
     * @return StompClient for receiving and sending messages. Call #StompClient.connect
     */
    public static StompClient over(Class clazz, String uri, Map<String, String> connectHttpHeaders) {
        try {
            if (Class.forName("org.java_websocket.WebSocket") != null && clazz == WebSocket.class) {
                return createStompClient(new WebSocketsConnectionProvider(uri, connectHttpHeaders));
            } else {
                throw new RuntimeException("Not supported overlay transport: " + clazz.getName());
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Class for transport " + clazz.getName() + " not included to project", e);
        }
    }

    private static StompClient createStompClient(ConnectionProvider connectionProvider) {
        return new StompClient(connectionProvider);
    }
}
