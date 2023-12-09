package ua.naiksoftware.stompclientexample;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import io.reactivex.CompletableTransformer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import ua.naiksoftware.stomp.AutomatedStompClient;
import ua.naiksoftware.stomp.Stomp;
import ua.naiksoftware.stomp.dto.LifecycleEvent;
import ua.naiksoftware.stomp.dto.StompHeader;
import ua.naiksoftware.stomp.StompClient;

import static ua.naiksoftware.stompclientexample.RestClient.ANDROID_EMULATOR_LOCALHOST;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    public static final String LOGIN = "login";
    public static final String PASSCODE = "passcode";

    private SimpleAdapter mAdapter;
    private final List<String> mDataSet = new ArrayList<>();
    private final SimpleDateFormat mTimeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private RecyclerView mRecyclerView;

    private final Gson mGson = new GsonBuilder().create();
    private AutomatedStompClient mStompClient;
    private Disposable mRestPingDisposable;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mRecyclerView = findViewById(R.id.recycler_view);
        mAdapter = new SimpleAdapter(mDataSet);
        mAdapter.setHasStableIds(true);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, true));

        mStompClient = new AutomatedStompClient(Stomp.ConnectionProvider.OKHTTP, "ws://" + ANDROID_EMULATOR_LOCALHOST + ":" + RestClient.SERVER_PORT + "/example-endpoint/websocket",
                AndroidSchedulers.mainThread());
    }

    @Override
    protected void onDestroy() {
        if (mRestPingDisposable != null) mRestPingDisposable.dispose();
        mStompClient.unsubscribeAll();
        super.onDestroy();
    }


    /**
     * function for connect stomp onCLick
     * @param view
     */
    public void connectStomp(View view) {
        List<StompHeader> headers = new ArrayList<>();
        headers.add(new StompHeader(LOGIN, "guest"));
        headers.add(new StompHeader(PASSCODE, "guest"));

        Consumer<? super LifecycleEvent> onLifecycleEvents = lifecycleEvent -> {
            switch (lifecycleEvent.getType()) {
                case OPENED:
                    Log.i(TAG, "rgsgfsdf ");
                    toast("Stomp connection opened");
                    break;
                case ERROR:
                    Log.e(TAG, "Stomp connection error", lifecycleEvent.getException());
                    toast("Stomp connection error");
                    break;
                case CLOSED:
                    toast("Stomp connection closed");
                    break;
                case FAILED_SERVER_HEARTBEAT:
                    toast("Stomp failed server heartbeat");
                    break;
            }
        };

        mStompClient.connect(headers, new Integer[]{1000, 1000}, onLifecycleEvents, throwable -> {});


        // Subscribe to Receive greetings
        mStompClient.subscribe(
                "/topic/greetings",
                topicMessage -> {
                    Log.d(TAG, "Received " + topicMessage.getPayload());
                    addItem(mGson.fromJson(topicMessage.getPayload(), EchoModel.class));
                },
                throwable -> {
                    Log.e(TAG, "Error on subscribe topic", throwable);
                }
        );
    }

    /**
     * function for disconnect stomp onCLick
     * @param view
     */
    public void disconnectStomp(View view) {
        mStompClient.disconnect();
    }

    /**
     * function for echo stomp onClick
     * @param v
     */
    public void sendEchoViaStomp(View v) {
        mStompClient.send(
                "/topic/hello-msg-mapping", "Echo STOMP " + mTimeFormat.format(new Date()),
                () -> {
                    Log.d(TAG, "STOMP echo send successfully");
                },
                throwable -> {
                    Log.e(TAG, "Error send STOMP echo", throwable);
                    toast(throwable.getMessage());
                }
        );
    }

    /**
     * function for echo rest onClick
     * @param v
     */
    public void sendEchoViaRest(View v) {
        mRestPingDisposable = RestClient.getInstance().getExampleRepository()
                .sendRestEcho("Echo REST " + mTimeFormat.format(new Date()))
                .compose(applySchedulers())
                .subscribe(() -> {
                    Log.d(TAG, "REST echo send successfully");
                }, throwable -> {
                    Log.e(TAG, "Error send REST echo", throwable);
                    toast(throwable.getMessage());
                });
    }


    private void addItem(EchoModel echoModel) {
        mDataSet.add(echoModel.getEcho() + " - " + mTimeFormat.format(new Date()));
        mAdapter.notifyDataSetChanged();
        mRecyclerView.smoothScrollToPosition(mDataSet.size() - 1);
    }

    private void toast(String text) {
        Log.i(TAG, text);
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    protected CompletableTransformer applySchedulers() {
        return upstream -> upstream
                .unsubscribeOn(Schedulers.newThread())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }
}
