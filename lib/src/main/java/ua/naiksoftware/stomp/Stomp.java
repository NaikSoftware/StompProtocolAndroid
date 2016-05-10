package ua.naiksoftware.stomp;

import org.java_websocket.WebSocket;

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

    public static StompClient over(Class claszz, String uri) {
        if (claszz == WebSocket.class) {
            return createStompClient(new WebSocketsConnectionProvider(uri));
        }

        throw new RuntimeException("Not supported overlay transport: " + claszz.getName());
    }

    private static StompClient createStompClient(ConnectionProvider connectionProvider) {
        return new StompClient(connectionProvider);
    }
}
