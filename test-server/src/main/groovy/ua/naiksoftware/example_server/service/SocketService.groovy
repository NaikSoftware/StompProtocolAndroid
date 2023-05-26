package ua.naiksoftware.example_server.service

import groovy.util.logging.Log4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import ua.naiksoftware.example_server.model.EchoModel

/**
 * Created by Naik on 23.02.17.
 */
@Log4j
@Service
class SocketService {

    @Autowired
    private SimpMessagingTemplate simpTemplate;

    def echoMessage(String message) {
        log.info("Start convertAndSend ${new Date()}")
        simpTemplate.convertAndSend('/topic/greetings', new EchoModel(message))
        log.info("End convertAndSend ${new Date()}")
    }
}
