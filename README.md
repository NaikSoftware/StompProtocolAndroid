# STOMP protocol via WebSocket for Android

[![Release](https://jitpack.io/v/NaikSoftware/StompProtocolAndroid.svg)](https://jitpack.io/#NaikSoftware/StompProtocolAndroid)

## Overview

This library provide support for STOMP protocol https://stomp.github.io/
At now library works only as client for backend with support STOMP, such as
NodeJS (stompjs or other) or Spring Boot (SockJS).

Add library as gradle dependency

```gradle
repositories { 
    jcenter()
    maven { url "https://jitpack.io" }
}
dependencies {
    implementation 'com.github.NaikSoftware:StompProtocolAndroid:{latest version}'
}
```

## Example backend (Spring Boot)

**WebSocketConfig.groovy**
```groovy
@Configuration
@EnableWebSocket
@EnableWebSocketMessageBroker
class WebSocketConfig extends AbstractWebSocketMessageBrokerConfigurer {

    @Override
    void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue", "/exchange");
//        config.enableStompBrokerRelay("/topic", "/queue", "/exchange"); // Uncomment for external message broker (ActiveMQ, RabbitMQ)
        config.setApplicationDestinationPrefixes("/topic", "/queue"); // prefix in client queries
        config.setUserDestinationPrefix("/user");
    }

    @Override
    void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/example-endpoint").withSockJS()
    }
}
```

**SocketController.groovy**
``` groovy
@Slf4j
@RestController
class SocketController {

    @MessageMapping('/hello-msg-mapping')
    @SendTo('/topic/greetings')
    EchoModel echoMessageMapping(String message) {
        log.debug("React to hello-msg-mapping")
        return new EchoModel(message.trim())
    }
}
```

Check out the full example server https://github.com/NaikSoftware/stomp-protocol-example-server

## Example library usage

**Basic usage**
``` java
 
public class MainActivity extends AppCompatActivity {

    // ...
 
    private AutoStompClient mStompClient;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mStompClient = new AutoStompClient(Stomp.ConnectionProvider.OKHTTP, "ws://" + ANDROID_EMULATOR_LOCALHOST + ":" + RestClient.SERVER_PORT + "/example-endpoint/websocket",
                AndroidSchedulers.mainThread());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mStompClient.unsubscribeAll();
    }


    public void connectStomp(View view) {
        List<StompHeader> headers = new ArrayList<>();
        headers.add(new StompHeader(LOGIN, "guest"));
        headers.add(new StompHeader(PASSCODE, "guest"));

        Consumer<? super LifecycleEvent> onLifecycleEvents = lifecycleEvent -> {
            switch (lifecycleEvent.getType()) {
                case OPENED:
                    toast("Stomp connection opened");
                    break;
                case ERROR:
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
        Consumer<? super Throwable> onThrow = throwable -> toast(throwable.getMessage());
        Consumer<? super StompMessage> onMessaged = topicMessage -> {
            toast("Received " + topicMessage.getPayload());
        };

        mStompClient.connect(headers, new Integer[]{1000, 1000}, onLifecycleEvents, onThrow);
        mStompClient.subscribe("/topic/greetings", onMessaged, onThrow);
    }

    public void disconnectStomp(View view) {
        mStompClient.disconnect();
    }

    public void sendEchoViaStomp(View v) {
        mStompClient.send(
                "/topic/hello-msg-mapping", "Echo STOMP " + mTimeFormat.format(new Date()),
                () -> toast("STOMP echo send successfully"), throwable -> toast(throwable.getMessage())
        );
    }

    private void toast(String text) {
        Log.i(TAG, text);
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
    
    // ...
 
}

```

See the full example https://github.com/NaikSoftware/StompProtocolAndroid/tree/master/example-client

Method `Stomp.over` consume class for create connection as first parameter.
You must provide dependency for lib and pass class.
At now supported connection providers:
- `org.java_websocket.WebSocket.class` ('org.java-websocket:Java-WebSocket:1.3.0')
- `okhttp3.WebSocket.class` ('com.squareup.okhttp3:okhttp:3.8.0')

You can add own connection provider. Just implement interface `ConnectionProvider`.
If you implement new provider, please create pull request :)

**Subscribe lifecycle connection**
``` java
mStompClient.lifecycle().subscribe(lifecycleEvent -> {
    switch (lifecycleEvent.getType()) {
    
        case OPENED:
            Log.d(TAG, "Stomp connection opened");
            break;
            
        case ERROR:
            Log.e(TAG, "Error", lifecycleEvent.getException());
            break;
            
        case CLOSED:
             Log.d(TAG, "Stomp connection closed");
             break;
    }
});
```

Library support just send & receive messages. ACK messages, transactions not implemented yet.
