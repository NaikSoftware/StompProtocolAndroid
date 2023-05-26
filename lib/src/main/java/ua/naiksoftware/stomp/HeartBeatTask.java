package ua.naiksoftware.stomp;

import android.util.Log;

import androidx.annotation.Nullable;

import java.util.concurrent.TimeUnit;

import io.reactivex.Scheduler;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import ua.naiksoftware.stomp.dto.StompCommand;
import ua.naiksoftware.stomp.dto.StompHeader;
import ua.naiksoftware.stomp.dto.StompMessage;

public class HeartBeatTask {

    private static final String TAG = HeartBeatTask.class.getSimpleName();

    private Scheduler scheduler;

    private int serverHeartbeat = 0;
    private int clientHeartbeat = 0;

    private int serverHeartbeatNew = 0;
    private int clientHeartbeatNew = 0;

    private transient long lastServerHeartBeat = 0;

    private transient Disposable clientSendHeartBeatTask;
    private transient Disposable serverCheckHeartBeatTask;

    private FailedListener failedListener;
    private SendCallback sendCallback;

    public HeartBeatTask(SendCallback sendCallback, @Nullable FailedListener failedListener) {
        this.failedListener = failedListener;
        this.sendCallback = sendCallback;
    }

    public void setServerHeartbeat(int serverHeartbeat) {
        this.serverHeartbeatNew = serverHeartbeat;
    }

    public void setClientHeartbeat(int clientHeartbeat) {
        this.clientHeartbeatNew = clientHeartbeat;
    }

    public int getServerHeartbeat() {
        return serverHeartbeatNew;
    }

    public int getClientHeartbeat() {
        return clientHeartbeatNew;
    }

    public boolean consumeHeartBeat(StompMessage message) {
        switch (message.getStompCommand()) {
            case StompCommand.CONNECTED:
                heartBeatHandshake(message.findHeader(StompHeader.HEART_BEAT));
                break;

            case StompCommand.SEND:
                abortClientHeartBeatSend();
                break;

            case StompCommand.MESSAGE:
                //a MESSAGE works as an hear-beat too.
                abortServerHeartBeatCheck();
                break;

            case StompCommand.UNKNOWN:
                if ("\n".equals(message.getPayload())) {
                    Log.d(TAG, "<<< PONG");
                    abortServerHeartBeatCheck();
                    return false;
                }
                break;
        }
        return true;
    }

    public void shutdown() {
        if (clientSendHeartBeatTask != null) {
            clientSendHeartBeatTask.dispose();
        }

        if (serverCheckHeartBeatTask != null) {
            serverCheckHeartBeatTask.dispose();
        }

        lastServerHeartBeat = 0;
    }

    /**
     * Analise heart-beat sent from server (if any), to adjust the frequency.
     * Startup the heart-beat logic.
     */
    private void heartBeatHandshake(final String heartBeatHeader) {
        if (heartBeatHeader != null) {
            // The heart-beat header is OPTIONAL
            final String[] heartbeats = heartBeatHeader.split(",");
            if (clientHeartbeatNew > 0) {
                //there will be heart-beats every MAX(<cx>,<sy>) milliseconds
                clientHeartbeat = Math.max(clientHeartbeatNew, Integer.parseInt(heartbeats[1]));
            }
            if (serverHeartbeatNew > 0) {
                //there will be heart-beats every MAX(<cx>,<sy>) milliseconds
                serverHeartbeat = Math.max(serverHeartbeatNew, Integer.parseInt(heartbeats[0]));
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

    private void scheduleServerHeartBeatCheck() {
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
                if (failedListener != null) {
                    failedListener.onServerHeartBeatFailed();
                }
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
    private void scheduleClientHeartBeat() {
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
        sendCallback.sendClientHeartBeat("\r\n");
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

    public interface FailedListener {
        void onServerHeartBeatFailed();
    }

    public interface SendCallback {
        void sendClientHeartBeat(String pingMessage);
    }
}
