package ua.naiksoftware.examplestompserver.config

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import org.springframework.messaging.simp.SimpAttributesContextHolder
import org.springframework.security.core.session.SessionDestroyedEvent
import org.springframework.web.socket.messaging.SessionConnectEvent
import org.springframework.web.socket.messaging.SessionDisconnectEvent
import ua.naiksoftware.examplestompserver.service.ExampleService

/**
 * Created by naik on 19.05.16.
 */
public final class RegistryListener implements ApplicationListener<ApplicationEvent> {

    private Logger logger = LoggerFactory.getLogger(RegistryListener)

    ExampleService exampleService

    public RegistryListener(ExampleService exampleService) {
        this.exampleService = exampleService
    }

    public void onApplicationEvent(ApplicationEvent event) {

        if (event instanceof SessionDestroyedEvent) {
            SessionDestroyedEvent e = (SessionDestroyedEvent) event;
            logger.debug("Session ${e.id} destroyed")
        }

        else if (event instanceof SessionConnectEvent) {
            SessionConnectEvent e = (SessionConnectEvent) event;
            logger.debug("Session user ${e.user.name} connected")
        }

        else if (event instanceof SessionDisconnectEvent) {
            SessionDisconnectEvent e = (SessionDisconnectEvent) event;
            Long roomId = (Long) SimpAttributesContextHolder.currentAttributes().getAttribute(ExampleService.SESSION_ATTR_ROOM_ID)
            if (roomId) {
                exampleService.removeUserFromSpectatorsRegistry(roomId, e.sessionId)
                logger.debug("Session user ${e.user.name} disconnected from room $roomId, status: ${e.closeStatus}")
            }
        }
    }
}
