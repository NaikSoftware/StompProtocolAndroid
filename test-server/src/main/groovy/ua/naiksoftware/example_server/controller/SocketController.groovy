package ua.naiksoftware.example_server.controller

import groovy.util.logging.Log4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ua.naiksoftware.example_server.model.EchoModel
import ua.naiksoftware.example_server.service.SocketService

/**
 * Created by Naik on 23.02.17.
 */
@Log4j
@RestController
class SocketController {

    @Autowired
    SocketService socketService

    @MessageMapping('/hello-msg-mapping')
    @SendTo('/topic/greetings')
    EchoModel echoMessageMapping(String message) {
        log.info("React to hello-msg-mapping")
        return new EchoModel(message.trim())
    }

    @RequestMapping(value = '/hello-convert-and-send', method = RequestMethod.POST)
    void echoConvertAndSend(@RequestParam('msg') String message) {
        socketService.echoMessage(message)
    }
}
