package ua.naiksoftware.examplestompserver.config

import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.messaging.MessageSecurityMetadataSourceRegistry
import org.springframework.security.config.annotation.web.socket.AbstractSecurityWebSocketMessageBrokerConfigurer

/**
 * Created by naik on 11.05.16.
 */
@Configuration
class WebSocketSecurityConfig extends AbstractSecurityWebSocketMessageBrokerConfigurer {

    @Override
    protected void configureInbound(MessageSecurityMetadataSourceRegistry messages) {
        messages.anyMessage().authenticated()
    }

    @Override
    protected boolean sameOriginDisabled() {
        true
    }
}
