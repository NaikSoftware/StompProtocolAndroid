package ua.naiksoftware.test_server.service

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import ua.naiksoftware.test_server.model.EchoModel

/**
 * Created by Naik on 23.02.17.
 */
@Slf4j
@Service
class SocketService {

    @Autowired
    private SimpMessagingTemplate simpTemplate;

    def echoMessage(String message) {
        log.debug("Start convertAndSend ${new Date()}")
        simpTemplate.convertAndSend('/topic/greetings', new EchoModel(message))
        log.debug("End convertAndSend ${new Date()}")
    }
}
