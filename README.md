# STOMP protocol via WebSocket for Android

## Overview

This library provide support for STOMP protocol https://stomp.github.io/
At now library works only as client for backend with support STOMP, such as
NodeJS (stompjs or other) or Spring Boot (SockJS).

## Example backend (Spring Boot)

**WebSocketConfig.groovy**
``` groovy
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig extends AbstractWebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/hello")/*.setAllowedOrigins('*')*/.withSockJS();
    }

}
```

**HelloSockController.groovy**
``` groovy
@RestController
class HelloSockController {

    @MessageMapping("/hello")
    @SendTo("/topic/greetings")
    def greeting(String msg) throws Exception {
        println("Receive greeting ${msg}")
        "ECHO: " + msg;
    }
}
```

## Example library usage

**Basic usage**
``` java
 private StompClient mStompClient;
 
 // ...
 
 mStompClient = Stomp.over(WebSocketClient.class, "ws://localhost:8080/app/hello/websocket");
 mStompClient.connect();
  
 mStompClient.topic("/topic/greetings").subscribe(topicMessage -> {
     Log.d(TAG, topicMessage.getPayload());
 });
  
 mStompClient.send("/app/hello", "My first STOMP message!");
  
 // ...
 
 mStompClient.disconnect();

```

Method `Stomp.over` consume class for create connection as first parameter.
You must provide dependency for lib and pass class.
At now supported connection providers:
- WebSocketClient.class ('org.java-websocket:Java-WebSocket:1.3.0')

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
