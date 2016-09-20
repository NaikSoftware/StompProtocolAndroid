package ua.naiksoftware.examplestompserver.config

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.AbstractWebSocketMessageBrokerConfigurer
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean
import ua.naiksoftware.examplestompserver.service.ExampleService

/**
 * Created by naik on 04.05.16.
 */
@Configuration
@EnableWebSocket
@EnableWebSocketMessageBroker
public class WebSocketConfig extends AbstractWebSocketMessageBrokerConfigurer {

    @Autowired private ExampleService exampleService

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue", "/exchange");
        config.setApplicationDestinationPrefixes("/board", "/topic", "/queue"); // prefix in client queries
//        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        def sockJs = registry.addEndpoint("/stomp")
        /*.setAllowedOrigins('*')*/.withSockJS(); // endpoint in client URI
    }

//    void configureClientInboundChannel(ChannelRegistration registration) {
//        registration.setInterceptors(new WebSocketInterceptor())
//    }

    @Bean
    public RegistryListener registryListener() {
        return new RegistryListener(exampleService)
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(8192 * 2);
        container.setMaxBinaryMessageBufferSize(8192 * 2);
        return container;
    }
}
