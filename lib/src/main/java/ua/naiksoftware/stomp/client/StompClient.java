package ua.naiksoftware.stomp.client;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Completable;
import io.reactivex.CompletableSource;
import io.reactivex.Flowable;
import io.reactivex.disposables.Disposable;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;
import ua.naiksoftware.stomp.ConnectionProvider;
import ua.naiksoftware.stomp.LifecycleEvent;
import ua.naiksoftware.stomp.StompHeader;

/**
 * Created by naik on 05.05.16.
 */
public class StompClient {

    private static final String TAG = StompClient.class.getSimpleName();

    public static final String SUPPORTED_VERSIONS = "1.1,1.0";
    public static final String DEFAULT_ACK = "auto";

    private final String tag = StompClient.class.getSimpleName();
    private final ConnectionProvider mConnectionProvider;
    private ConcurrentHashMap<String, String> mTopics;
    private boolean mConnected;
    private boolean isConnecting;
    private boolean legacyWhitespace;

    private PublishSubject<StompMessage> mMessageStream;
    private ConcurrentHashMap<String, Flowable<StompMessage>> mStreamMap;
    private final BehaviorSubject<Boolean> mConnectionStream;
    private Parser parser;
    private Disposable mLifecycleDisposable;
    private Disposable mMessagesDisposable;
    private List<StompHeader> mHeaders;
    private int heartbeat;

    public StompClient(ConnectionProvider connectionProvider) {
        mConnectionProvider = connectionProvider;
        mMessageStream = PublishSubject.create();
        mStreamMap = new ConcurrentHashMap<>();
        mConnectionStream = BehaviorSubject.createDefault(false);
        parser = Parser.NONE;
    }

    public enum Parser {
        NONE,
        RABBITMQ
    }

    /**
     * Set the wildcard parser for Topic subscription.
     * <p>
     * Right now, the only options are NONE and RABBITMQ.
     * <p>
     * When set to RABBITMQ, topic subscription allows for RMQ-style wildcards.
     * <p>
     * See more info <a href="https://www.rabbitmq.com/tutorials/tutorial-five-java.html">here</a>.
     *
     * @param parser Set to NONE by default
     */
    public void setParser(Parser parser) {
        this.parser = parser;
    }

    /**
     * Sets the heartbeat interval to request from the server.
     * <p>
     * Not very useful yet, because we don't have any heartbeat logic on our side.
     *
     * @param ms heartbeat time in milliseconds
     */
    public void setHeartbeat(int ms) {
        heartbeat = ms;
        mConnectionProvider.setHeartbeat(ms).subscribe();
    }

    /**
     * Connect without reconnect if connected
     */
    public void connect() {
        connect(null);
    }

    /**
     * Connect to websocket. If already connected, this will silently fail.
     *
     * @param _headers HTTP headers to send in the INITIAL REQUEST, i.e. during the protocol upgrade
     */
    public void connect(@Nullable List<StompHeader> _headers) {

        Log.d(TAG, "Connect");

        mHeaders = _headers;

        if (mConnected) {
            Log.d(TAG, "Already connected, ignore");
            return;
        }
        mLifecycleDisposable = mConnectionProvider.lifecycle()
                .subscribe(lifecycleEvent -> {
                    switch (lifecycleEvent.getType()) {
                        case OPENED:
                            List<StompHeader> headers = new ArrayList<>();
                            headers.add(new StompHeader(StompHeader.VERSION, SUPPORTED_VERSIONS));
                            headers.add(new StompHeader(StompHeader.HEART_BEAT, "0," + heartbeat));
                            if (_headers != null) headers.addAll(_headers);
                            mConnectionProvider.send(new StompMessage(StompCommand.CONNECT, headers, null).compile(legacyWhitespace))
                                    .subscribe();
                            break;

                        case CLOSED:
                            Log.d(TAG, "Socket closed");
                            setConnected(false);
                            isConnecting = false;
                            break;

                        case ERROR:
                            Log.d(TAG, "Socket closed with error");
                            setConnected(false);
                            isConnecting = false;
                            break;
                    }
                });

        isConnecting = true;
        mMessagesDisposable = mConnectionProvider.messages()
                .map(StompMessage::from)
                .doOnNext(this::callSubscribers)
                .filter(msg -> msg.getStompCommand().equals(StompCommand.CONNECTED))
                .subscribe(stompMessage -> {
                    setConnected(true);
                    isConnecting = false;

                });
    }

    private void setConnected(boolean connected) {
        mConnected = connected;
        mConnectionStream.onNext(mConnected);
    }

    /**
     * Disconnect from server, and then reconnect with the last-used headers
     */
    public void reconnect() {
        disconnectCompletable()
                .subscribe(() -> connect(mHeaders),
                        e -> Log.e(tag, "Disconnect error", e));
    }

    public Completable send(String destination) {
        return send(destination, null);
    }

    public Completable send(String destination, String data) {
        return send(new StompMessage(
                StompCommand.SEND,
                Collections.singletonList(new StompHeader(StompHeader.DESTINATION, destination)),
                data));
    }

    public Completable send(@NonNull StompMessage stompMessage) {
        Completable completable = mConnectionProvider.send(stompMessage.compile(legacyWhitespace));
        CompletableSource connectionComplete = mConnectionStream
                .filter(isConnected -> isConnected)
                .firstOrError().toCompletable();
        return completable
                .startWith(connectionComplete);
    }

    private void callSubscribers(StompMessage stompMessage) {
        mMessageStream.onNext(stompMessage);
    }

    public Flowable<LifecycleEvent> lifecycle() {
        return mConnectionProvider.lifecycle().toFlowable(BackpressureStrategy.BUFFER);
    }

    public void disconnect() {
        disconnectCompletable().subscribe(() -> {}, e -> Log.e(tag, "Disconnect error", e));
    }

    public Completable disconnectCompletable() {
        if (mLifecycleDisposable != null) {
            mLifecycleDisposable.dispose();
        }
        if (mMessagesDisposable != null) {
            mMessagesDisposable.dispose();
        }
        return mConnectionProvider.disconnect()
                .doOnComplete(() -> setConnected(false));
    }

    public Flowable<StompMessage> topic(String destinationPath) {
        return topic(destinationPath, null);
    }

    public Flowable<StompMessage> topic(@NonNull String destPath, List<StompHeader> headerList) {
        if (destPath == null)
            return Flowable.error(new IllegalArgumentException("Topic path cannot be null"));
        else if (!mStreamMap.containsKey(destPath))
            mStreamMap.put(destPath,
                    mMessageStream
                            .filter(msg -> matches(destPath, msg))
                            .toFlowable(BackpressureStrategy.BUFFER)
                            .doOnSubscribe(disposable -> subscribePath(destPath, headerList).subscribe())
                            .doFinally(() -> unsubscribePath(destPath).subscribe())
                            .share()
            );
        return mStreamMap.get(destPath);
    }

    /**
     * Reverts to the old frame formatting, which included two newlines between the message body
     * and the end-of-frame marker.
     * <p>
     * Legacy: Body\n\n^@
     * <p>
     * Default: Body^@
     *
     * @param legacyWhitespace whether to append an extra two newlines
     * @see <a href="http://stomp.github.io/stomp-specification-1.2.html#STOMP_Frames">The STOMP spec</a>
     */
    public void setLegacyWhitespace(boolean legacyWhitespace) {
        this.legacyWhitespace = legacyWhitespace;
    }

    private boolean matches(String path, StompMessage msg) {
        String dest = msg.findHeader(StompHeader.DESTINATION);
        if (dest == null) return false;
        boolean ret;

        switch (parser) {
            case NONE:
                ret = path.equals(dest);
                break;

            case RABBITMQ:
                // for example string "lorem.ipsum.*.sit":

                // split it up into ["lorem", "ipsum", "*", "sit"]
                String[] split = path.split("\\.");
                ArrayList<String> transformed = new ArrayList<>();
                // check for wildcards and replace with corresponding regex
                for (String s : split) {
                    switch (s) {
                        case "*":
                            transformed.add("[^.]+");
                            break;
                        case "#":
                            // TODO: make this work with zero-word
                            // e.g. "lorem.#.dolor" should ideally match "lorem.dolor"
                            transformed.add(".*");
                            break;
                        default:
                            transformed.add(s);
                            break;
                    }
                }
                // at this point, 'transformed' looks like ["lorem", "ipsum", "[^.]+", "sit"]
                StringBuilder sb = new StringBuilder();
                for (String s : transformed) {
                    if (sb.length() > 0) sb.append("\\.");
                    sb.append(s);
                }
                String join = sb.toString();
                // join = "lorem\.ipsum\.[^.]+\.sit"

                ret = dest.matches(join);
                break;

            default:
                ret = false;
                break;
        }

        return ret;
    }

    private Completable subscribePath(String destinationPath, @Nullable List<StompHeader> headerList) {
        String topicId = UUID.randomUUID().toString();

        if (mTopics == null) mTopics = new ConcurrentHashMap<>();

        // Only continue if we don't already have a subscription to the topic
        if (mTopics.containsKey(destinationPath)) {
            Log.d(TAG, "Attempted to subscribe to already-subscribed path!");
            return Completable.complete();
        }

        mTopics.put(destinationPath, topicId);
        List<StompHeader> headers = new ArrayList<>();
        headers.add(new StompHeader(StompHeader.ID, topicId));
        headers.add(new StompHeader(StompHeader.DESTINATION, destinationPath));
        headers.add(new StompHeader(StompHeader.ACK, DEFAULT_ACK));
        if (headerList != null) headers.addAll(headerList);
        return send(new StompMessage(StompCommand.SUBSCRIBE,
                headers, null));
    }


    private Completable unsubscribePath(String dest) {
        mStreamMap.remove(dest);

        String topicId = mTopics.get(dest);
        mTopics.remove(dest);

        Log.d(TAG, "Unsubscribe path: " + dest + " id: " + topicId);

        return send(new StompMessage(StompCommand.UNSUBSCRIBE,
                Collections.singletonList(new StompHeader(StompHeader.ID, topicId)), null));
    }

    public boolean isConnected() {
        return mConnected;
    }

    public boolean isConnecting() {
        return isConnecting;
    }
}
