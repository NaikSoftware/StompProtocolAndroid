package ua.naiksoftware.stomp;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.concurrent.TimeUnit;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import ua.naiksoftware.stomp.client.StompCommand;
import ua.naiksoftware.stomp.client.StompMessage;

/**
 * Created by forresthopkinsa on 8/8/2017.
 * <p>
 * Created because there was a lot of shared code between JWS and OkHttp connection providers.
 */

abstract class AbstractConnectionProvider implements ConnectionProvider {

    private static final String TAG = AbstractConnectionProvider.class.getSimpleName();

    private transient Disposable clientSendHeartBeatTask;
    private transient Disposable serverCheckHeartBeatTask;
    private Scheduler scheduler;

    private int serverHeartbeat = 0;
    private int clientHeartbeat = 0;

    private transient long lastServerHeartBeat = 0;


    @NonNull
    private final PublishSubject<LifecycleEvent> mLifecycleStream;
    @NonNull
    private final PublishSubject<String> mMessagesStream;

    AbstractConnectionProvider() {
        mLifecycleStream = PublishSubject.create();
        mMessagesStream = PublishSubject.create();
    }

    @NonNull
    @Override
    public Observable<String> messages() {
        return mMessagesStream.startWith(initSocket().toObservable());
    }

    /**
     * Simply close socket.
     * <p>
     * For example:
     * <pre>
     * webSocket.close();
     * </pre>
     */
    abstract void rawDisconnect();

    @Override
    public Completable disconnect() {
        if (clientSendHeartBeatTask != null) {
            clientSendHeartBeatTask.dispose();
        }

        if (serverCheckHeartBeatTask != null) {
            serverCheckHeartBeatTask.dispose();
        }

        lastServerHeartBeat = 0;

        // TODO shutdown Schedulers.io() is not a good idea
//        scheduler.shutdown();

        return Completable
                .fromAction(this::rawDisconnect);
    }

    private Completable initSocket() {
        return Completable
                .fromAction(this::createWebSocketConnection);
    }

    /**
     * Most important method: connects to websocket and notifies program of messages.
     * <p>
     * See implementations in OkHttpConnectionProvider and WebSocketsConnectionProvider.
     */
    abstract void createWebSocketConnection();

    @NonNull
    @Override
    public Completable send(String stompMessage) {
        return Completable.fromCallable(() -> {
            if (getSocket() == null) {
                throw new IllegalStateException("Not connected yet");
            } else {
                Log.d(TAG, "Send STOMP message: " + stompMessage);
                rawSend(stompMessage);
                return null;
            }
        });
    }

    /**
     * Just a simple message send.
     * <p>
     * For example:
     * <pre>
     * webSocket.send(stompMessage);
     * </pre>
     *
     * @param stompMessage message to send
     */
    abstract void rawSend(String stompMessage);

    /**
     * Get socket object.
     * Used for null checking; this object is expected to be null when the connection is not yet established.
     * <p>
     * For example:
     * <pre>
     * return webSocket;
     * </pre>
     */
    @Nullable
    abstract Object getSocket();

    void emitLifecycleEvent(@NonNull LifecycleEvent lifecycleEvent) {
        Log.d(TAG, "Emit lifecycle event: " + lifecycleEvent.getType().name());
        mLifecycleStream.onNext(lifecycleEvent);
    }

    void emitMessage(String stompMessage) {
        //TODO: Why we don't publish a StompMessage, instead of String? will this connection provider work with other protocol?
        final StompMessage sm = StompMessage.from(stompMessage);

        if (StompCommand.CONNECTED.equals(sm.getStompCommand())) {
            Log.d(TAG, "<<< CONNECTED");
            heartBeatHandshake(sm.findHeader(StompHeader.HEART_BEAT));
        } else if (StompCommand.SEND.equals(sm.getStompCommand())) {
            abortClientHeartBeatSend();
        } else if (StompCommand.MESSAGE.equals(sm.getStompCommand())) {
            //a MESSAGE works as an hear-beat too.
            abortServerHeartBeatCheck();
        }

        if (stompMessage.equals("\n")) {
            Log.d(TAG, "<<< PONG");
            abortServerHeartBeatCheck();
        } else {
            Log.d(TAG, "Receive STOMP message: " + stompMessage);
            mMessagesStream.onNext(stompMessage);
        }
    }

    @NonNull
    @Override
    public Observable<LifecycleEvent> lifecycle() {
        return mLifecycleStream;
    }

    /**
     * Analise heart-beat sent from server (if any), to adjust the frequency.
     * Startup the heart-beat logic.
     *
     * @param heartBeatHeader
     */
    private void heartBeatHandshake(final String heartBeatHeader) {
        if (heartBeatHeader != null) {
            // The heart-beat header is OPTIONAL
            final String[] heartbeats = heartBeatHeader.split(",");
            if (clientHeartbeat > 0) {
                //there will be heart-beats every MAX(<cx>,<sy>) milliseconds
                clientHeartbeat = Math.max(clientHeartbeat, Integer.parseInt(heartbeats[1]));
            }
            if (serverHeartbeat > 0) {
                //there will be heart-beats every MAX(<cx>,<sy>) milliseconds
                serverHeartbeat = Math.max(serverHeartbeat, Integer.parseInt(heartbeats[0]));
            }
        }
        if (clientHeartbeat > 0 || serverHeartbeat > 0) {
            scheduler = Schedulers.io();
            if (clientHeartbeat > 0) {
                //client MUST/WANT send heart-beat
                Log.d(TAG, "Client will send heart-beat every " + clientHeartbeat + " ms");
                scheduleClientHeartBeat();
            }
            if (serverHeartbeat > 0) {
                Log.d(TAG, "Client will listen to server heart-beat every " + serverHeartbeat + " ms");
                //client WANT to listen to server heart-beat
                scheduleServerHeartBeatCheck();

                // initialize the server heartbeat
                lastServerHeartBeat = System.currentTimeMillis();
            }
        }
    }

    protected void scheduleServerHeartBeatCheck() {
        if (serverHeartbeat > 0 && scheduler != null) {
            final long now = System.currentTimeMillis();
            Log.d(TAG, "Scheduling server heart-beat to be checked in " + serverHeartbeat + " ms and now is '" + now + "'");
            //add some slack on the check
            serverCheckHeartBeatTask = scheduler.scheduleDirect(() ->
                    checkServerHeartBeat(), serverHeartbeat, TimeUnit.MILLISECONDS);
        }
    }

    private void checkServerHeartBeat() {
        if (serverHeartbeat > 0) {
            final long now = System.currentTimeMillis();
            //use a forgiving boundary as some heart beats can be delayed or lost.
            final long boundary = now - (3 * serverHeartbeat);
            //we need to check because the task could failed to abort
            if (lastServerHeartBeat < boundary) {
                Log.d(TAG, "It's a sad day ;( Server didn't send heart-beat on time. Last received at '" + lastServerHeartBeat + "' and now is '" + now + "'");
                final LifecycleEvent failedServerHeartBeat = new LifecycleEvent(LifecycleEvent.Type.FAILED_SERVER_HEARTBEAT);
                emitLifecycleEvent(failedServerHeartBeat);
            } else {
                Log.d(TAG, "We were checking and server sent heart-beat on time. So well-behaved :)");
                lastServerHeartBeat = System.currentTimeMillis();
            }
        }
    }

    /**
     * Used to abort the server heart-beat check.
     */
    private void abortServerHeartBeatCheck() {
        lastServerHeartBeat = System.currentTimeMillis();
        Log.d(TAG, "Aborted last check because server sent heart-beat on time ('" + lastServerHeartBeat + "'). So well-behaved :)");
        if (serverCheckHeartBeatTask != null) {
            serverCheckHeartBeatTask.dispose();
        }
        scheduleServerHeartBeatCheck();
    }

    /**
     * Schedule a client heart-beat if clientHeartbeat > 0.
     */
    public void scheduleClientHeartBeat() {
        if (clientHeartbeat > 0 && scheduler != null) {
            Log.d(TAG, "Scheduling client heart-beat to be sent in " + clientHeartbeat + " ms");
            clientSendHeartBeatTask = scheduler.scheduleDirect(() ->
                    sendClientHeartBeat(), clientHeartbeat, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Send the raw heart-beat to the server.
     */
    private void sendClientHeartBeat() {
        this.rawSend("\r\n");
        Log.d(TAG, "PING >>>");
        //schedule next client heart beat
        this.scheduleClientHeartBeat();
    }

    /**
     * Used when we have a scheduled heart-beat and we send a new message to the server.
     * The new message will work as an heart-beat so we can abort current one and schedule another
     */
    private void abortClientHeartBeatSend() {
        if (clientSendHeartBeatTask != null) {
            clientSendHeartBeatTask.dispose();
        }
        scheduleClientHeartBeat();
    }

    /**
     * Set the server heart-beat
     *
     * @param ms milliseconds
     */
    public void setServerHeartbeat(int ms) {
        this.serverHeartbeat = ms;
    }

    /**
     * Set the client heart-beat
     *
     * @param ms milliseconds
     */
    public void setClientHeartbeat(int ms) {
        this.clientHeartbeat = ms;
    }
}
