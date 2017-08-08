# STOMP protocol via WebSocket for Android

[![Release](https://jitpack.io/v/forresthopkinsa/StompProtocolAndroid.svg)](https://jitpack.io/#forresthopkinsa/StompProtocolAndroid)

## Overview

**Note that this is a FORK of a project by NaikSoftware! This version is made to avoid using RetroLambda! (Scroll down to see other changes)**

This library provides support for [STOMP protocol](https://stomp.github.io/) over Websockets.

At now library works only as client for any backend that supports STOMP, such as
NodeJS (e.g. using StompJS) or Spring Boot ([with WebSocket support](https://spring.io/guides/gs/messaging-stomp-websocket/)).

Add library as gradle dependency (Versioning info [here](https://jitpack.io/#forresthopkinsa/StompProtocolAndroid)):

```gradle
repositories { 
    jcenter()
    maven { url "https://jitpack.io" }
}
dependencies {
    compile 'com.github.forresthopkinsa:StompProtocolAndroid:1.4.0'
}
```

You can use this library two ways:

- Using the old JACK toolchain
  - If you have Java 8 compatiblity and Jack enabled, this library will work for you
- Using the new Native Java 8 support
  - As of this writing, you must be using Android Studio Canary to use this feature.
  - Has been tested in the following environments:
    - Canary 9, Gradle plugin v3.0.0-alpha9
    - Canary 8, Gradle plugin v3.0.0-alpha8
  - It *should* work in all 3.0.0+ versions
  - You can find more info on the [Releases Page](https://github.com/forresthopkinsa/StompProtocolAndroid/releases)

However, *this fork is NOT compatible with Retrolambda.*
If you have RL as a dependency, then you should be using the [upstream version](https://github.com/NaikSoftware/StompProtocolAndroid) of this project!

Finally, please take bugs and questions to the [Issues page](https://github.com/forresthopkinsa/StompProtocolAndroid/issues)! I'll try to answer within one business day.

## Example backend (Spring Boot)

**WebSocketConfig.java**
```java
@Configuration
@EnableWebSocket
@EnableWebSocketMessageBroker
class WebSocketConfig extends AbstractWebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // We're using Spring's built-in message broker, with prefix "/topic"
        config.enableSimpleBroker("/topic");
        // This is the prefix for client requests
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/example-endpoint");
    }
}
```

**SocketController.java**
``` java
@RestController
class SocketController {

    @MessageMapping('/hello')
    @SendTo('/topic/greetings')
    public String greeting(String name) {
        return "Hello, " + name + "!";
    }
}
```

Check out the [upstream example server](https://github.com/NaikSoftware/stomp-protocol-example-server)

## Example library usage

**Basic usage**
``` java
 import okhttp3.OkHttpClient;
 import okhttp3.Request;
 import okhttp3.Response;
 import okhttp3.WebSocket;
 import okhttp3.WebSocketListener;
 
 // ...
 
 StompClient client = Stomp.over(Stomp.ConnectionProvider.OKHTTP, "http://localhost/example-endpoint");
 client.connect();
  
 client.topic("/topic/greetings").subscribe(message -> {
     Log.i(TAG, "Received message: " + message.getPayload());
 });
  
 client.send("/app/hello", "world").subscribe(
     aVoid -> Log.d(TAG, "Sent data!"),
     error -> Log.e(TAG, "Encountered error while sending data!", error)
 );
  
 // ...

 // close socket connection when finished or exiting
 client.disconnect();

```

See the [upstream example](https://github.com/NaikSoftware/StompProtocolAndroid/tree/master/example-client)

Method `Stomp.over` uses an enum to know what connection provider to use.

Currently supported connection providers:
- `Stomp.ConnectionProvider.JWS` ('org.java-websocket:Java-WebSocket:1.3.2')
- `Stomp.ConnectionProvider.OKHTTP` ('com.squareup.okhttp3:okhttp:3.8.1')

You can add own connection provider. Just implement interface `ConnectionProvider`.
If you implement new provider, please create pull request :)

**Subscribe lifecycle connection**
``` java
client.lifecycle().subscribe(lifecycleEvent -> {
    switch (lifecycleEvent.getType()) {
        case OPENED:
            Log.d(TAG, "Stomp connection opened");
            break;
        case CLOSED:
             Log.d(TAG, "Stomp connection closed");
             break;
        case ERROR:
            Log.e(TAG, "Stomp connection error", lifecycleEvent.getException());
            break;
    }
});
```

**Custom client**

You can use a custom OkHttpClient (for example, [if you want to allow untrusted HTTPS](https://gist.github.com/grow2014/b6969d8f0cfc0f0a1b2bf12f84973dec)) using the four-argument overload of Stomp.over, like so:

``` java
client = Stomp.over(Stomp.ConnectionProvider.OKHTTP, address, null, getUnsafeOkHttpClient());
```

Yes, it's safe to pass `null` for either (or both) of the last two arguments. That's exactly what the shorter overloads do.

Note: This method is only supported using OkHttp, not JWS.

**Support**

Right now, the library only supports sending and receiving messages. ACK messages and transactions are not implemented yet.

## Changes in this fork

**Build changes**

The upstream master is based on Retrolambda. This version is based on Native Java 8 compilation,
although it should also work with Jack. It will *not* work with Retrolambda.

**Code changes**

These are the possible changes you need to make to your code for this branch, if you were using the upstream before:

- Disconnecting is now mandatory
  - Previously, the socket would automatically disconnect if nothing was listening to it
  - Now, it will not disconnect unless you explicitly run client.disconnect()
- send() now returns a Completable
  - Previously, it returned an Observable\<Void\>
  - Now, it and all its overloads return a Completable, which is functionally about the same
  - **However**, Completable does not inherit from Observable, so there are a couple differences:
    - Of course, if you were storing send() in an Observable, you'll have to change that
    - Additionally, if you were inheriting onNext before, you're going to have to adjust it:
      - Old way, deprecated:
        ``` java
        client.send("/app/hello", "world").subscribe(
            aVoid -> Log.d(TAG, "Sent data!"),
            error -> Log.e(TAG, "Encountered error while sending data!", error)
        );
        ```
        !! -v:
        ``` java
        client.send("/app/hello", "world").subscribe(new Subscriber<Void>() {
            @Override
            public void onNext(Void aVoid) {
                Log.d(TAG, "Sent data!");
            }
            @Override
            public void onError(Throwable error) {
                Log.e(TAG, "Encountered error while sending data!", error);
            }
            @Override
            public void onCompleted() {} // useless
        });
        ```
      - New way of handling it:
        ``` java
        client.send("/app/hello", "world").subscribe(
            () -> Log.d(TAG, "Sent data!"),
            error -> Log.e(TAG, "Encountered error while sending data!", error)
        );
        ```
        Or if you just can't get enough of anonymous classes:
        ``` java
        client.send("/app/hello", "world").subscribe(new Subscriber<Object /*This can be anything*/ >() {
            @Override
            public void onCompleted() {
                Log.d(TAG, "Sent data!");
            }
            @Override
            public void onError(Throwable error) {
                Log.e(TAG, "Encountered error while sending data!", error);
            }
            @Override
            public void onNext(Object o) {} // useless
        });
        ```
    - Be sure to implement this change, because the IDE might not catch the error.
- Passing null as the topic path now throws an exception
  - Previously, it was supposed to silently fail, although it would probably hit a NPE first (untested)
  - Now it throws an IllegalArgumentException
- Connection Provider is selected by an enum
  - It used to be done by passing it a WebSocket class from either JWS or OkHttp
  - New way of using it can be seen in the examples above

## Additional Reading

- [Spring + Websockets + STOMP](https://spring.io/guides/gs/messaging-stomp-websocket/)
- [STOMP Protocol](http://stomp.github.io/)
- [Spring detailed documentation](https://docs.spring.io/spring/docs/current/spring-framework-reference/html/websocket.html#websocket-stomp)
- [Create an Unsafe OkHttp Client](https://gist.github.com/grow2014/b6969d8f0cfc0f0a1b2bf12f84973dec)
  - (for developing with invalid SSL certs)