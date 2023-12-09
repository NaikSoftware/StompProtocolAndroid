package ua.naiksoftware.stomp;


import io.reactivex.Scheduler;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

import ua.naiksoftware.stomp.dto.LifecycleEvent;
import ua.naiksoftware.stomp.dto.StompHeader;
import ua.naiksoftware.stomp.dto.StompMessage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class AutomatedStompClient {
    private final StompClient mStompClient;
    private final Scheduler mScheduler;
    private final CompositeDisposable compositeDisposable;
    private final Map<String, Disposable> destinationDisposable;


    public AutomatedStompClient (Stomp.ConnectionProvider cp, String uri, Scheduler scheduler) {
        mStompClient = Stomp.over(cp, uri);
        mScheduler = scheduler;
        compositeDisposable = new CompositeDisposable();
        destinationDisposable = new HashMap<>();
    }


    public void disconnect(){
        mStompClient.disconnect();
        unsubscribeAll();
    }
    public void connect(List<StompHeader> headers, Integer[] heartbeat, Consumer<? super LifecycleEvent> onLifecycleEvents, Consumer<? super Throwable> onThrow){
        assert heartbeat.length==2;

        unsubscribeAll();
        mStompClient.withClientHeartbeat(heartbeat[0]).withServerHeartbeat(heartbeat[1]);
        compositeDisposable.add(
                mStompClient.lifecycle().subscribeOn(Schedulers.io())
                .observeOn(mScheduler).subscribe(onLifecycleEvents, onThrow)
        );
        mStompClient.connect(headers);
    }
    public void subscribe(String destinationPath, Consumer<? super StompMessage> onLifecycleEvents, Consumer<? super Throwable> onThrow){
        Disposable cd = mStompClient.topic(destinationPath).subscribeOn(Schedulers.io())
                .observeOn(mScheduler).subscribe(onLifecycleEvents, onThrow);
        compositeDisposable.add(cd);
        destinationDisposable.put(destinationPath, cd);
    }
    public void send(String destinationPath, String data, Action onSent, Consumer<? super Throwable> onThrow){
        compositeDisposable.add(
                mStompClient.send(destinationPath, data).subscribeOn(Schedulers.io())
                        .observeOn(mScheduler).subscribe(onSent, onThrow)
        );
    }
    public void unsubscribe(String destinationPath, Action onSent, Consumer<? super Throwable> onThrow){
        Disposable cd = destinationDisposable.get(destinationPath);
        if(cd != null) cd.dispose();
        destinationDisposable.remove(destinationPath);
    }
    public void unsubscribeAll() {
        compositeDisposable.dispose();
    }
}
