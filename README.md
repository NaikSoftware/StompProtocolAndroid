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

**HelloSockController**
``` groovy
@RestController
class BoardSockController {

    @MessageMapping("/hello")
    @SendTo("/topic/greetings")
    public String greeting(String msg) throws Exception {
        println("Receive greeting ${msg}")
        return "ECHO: " + msg;
    }
}
```

## Example library usage

**Basic usage**
``` java
 private StompClient mStompClient;
 
 // ...
 
 mStompClient = Stomp.over(WebSocketClient.class, Config.STOMP_BASE_URI);
 mStompClient.connect();
  
 mStompClient.topic("/topic/greetings").subscribe(topicMessage -> {
     Log.d(TAG, topicMessage.getPayload());
 });
  
 mStompClient.send("/app/hello", "My first STOMP message!");
  
 // ...
 
 mStompClient.disconnect();

```

**Subscribe lifecycle connection**
``` java
mStompClient.lifecycle().subscribe(lifecycleEvent -> {
    switch (lifecycleEvent.getType()) {
        case ERROR:
            if (mCallback != null) mCallback.showError(lifecycleEvent.getException().getMessage());
            break;
        case CLOSED:
             LOGD(TAG, "Stomp connection closed");
    }
});
```
