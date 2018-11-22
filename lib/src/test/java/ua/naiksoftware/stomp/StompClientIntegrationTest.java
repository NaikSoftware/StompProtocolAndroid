package ua.naiksoftware.stomp;

import android.util.Log;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ua.naiksoftware.stomp.client.StompClient;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;


@RunWith(PowerMockRunner.class)
@PrepareForTest({Log.class})
@PowerMockIgnore("javax.net.ssl.*")
public class StompClientIntegrationTest {

    private static final String TAG = StompClientIntegrationTest.class.getSimpleName();

    @BeforeClass
    public static void init() {
        PowerMockito.mockStatic(Log.class);
        Mockito.when(Log.i(anyString(), anyString())).then(i ->  {System.out.println((String) i.getArgument(0) + " --> " + (String) i.getArgument(1) ); return null; });
        Mockito.when(Log.w(anyString(), anyString())).then(i ->  {System.out.println((String) i.getArgument(0) + " --> " + (String) i.getArgument(1) ); return null; });
        Mockito.when(Log.d(anyString(), anyString())).then(i ->  {System.out.println((String) i.getArgument(0) + " --> " + (String) i.getArgument(1) ); return null; });
        Mockito.when(Log.e(anyString(), anyString())).then(i ->  {System.out.println((String) i.getArgument(0) + " --> " + (String) i.getArgument(1) ); return null; });

        //TODO: bootstap a spring boot test server, so we can run tests.
    }

    @AfterClass
    public static void terminate() {
        //TODO: teardown  a spring boot test server, so we can run tests.
    }


    @Test
    public void callWebsocket() {
        //TODO: uncomment and create proper testing.

//        final List<String> messages = new ArrayList<>();
//
//        final Map<String, String> connectHttpHeaders = new HashMap<>();
//        connectHttpHeaders.put("Authorization", "Basic bHVhbDpwYXNz");
//
//        final StompClient mStompClient = Stomp.over(Stomp.ConnectionProvider.OKHTTP, "ws://localhost:8080/notifications/websocket", connectHttpHeaders);
//        //don't care...but server will force it
//        mStompClient.withClientHeartbeat(10000).withServerHeartbeat(10000);
//
//        mStompClient.topic("/topic/notification/system/demo/username/lual").subscribe(topicMessage -> {
//           Log.i(TAG,topicMessage.getPayload());
//           messages.add(topicMessage.getPayload());
//        });
//
//        mStompClient.lifecycle().subscribe(lifecycleEvent -> {
//            switch (lifecycleEvent.getType()) {
//
//                case OPENED:
//                    Log.i(TAG,"Stomp connection opened");
//                    break;
//
//                case ERROR:
//                    Log.i(TAG,"Error " + lifecycleEvent.getException());
//                    break;
//
//                case CLOSED:
//                    Log.i(TAG,"Stomp connection closed");
//                    break;
//            }
//        });
//
//        mStompClient.connect();
//
//        await().atMost(5, MINUTES).until( messages::size, is(10));
//
//        mStompClient.disconnect();
      }


}