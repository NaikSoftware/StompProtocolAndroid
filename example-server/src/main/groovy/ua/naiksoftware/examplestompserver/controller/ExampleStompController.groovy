package ua.naiksoftware.examplestompserver.controller

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageExceptionHandler
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.annotation.SendToUser
import org.springframework.messaging.simp.annotation.SubscribeMapping
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.RestController
import ua.naiksoftware.examplestompserver.UserPrincipal
import ua.naiksoftware.examplestompserver.service.ExampleService

/**
 * Created by naik on 18.09.16.
 */
@RestController
class ExampleStompController {

    Logger logger = LoggerFactory.getLogger(ExampleStompController)

    @Autowired ExampleService exampleService

    @SubscribeMapping("/game-history/{id}")
    @SendToUser(broadcast = false)
    def allData(@DestinationVariable("id") Long roomId, @AuthenticationPrincipal UserPrincipal principal,
                   SimpMessageHeaderAccessor headerAccessor) throws Exception {

        logger.debug("Get all data for room ${roomId}")
        headerAccessor.sessionAttributes.put(ExampleService.SESSION_ATTR_ROOM_ID, roomId)
        exampleService.getAllHistoryForRoom(roomId, headerAccessor.sessionId, principal);
    }

    /**
     * Send all server errors to clients (use userId as endpoint and send only to 1 connected client (broadcast=false))
     */
    @MessageExceptionHandler
    @SendToUser(value = "/exchange/game/errors", broadcast = false)
    def handleError(Exception e) {
        e.printStackTrace()
        return e.getMessage();
    }
}
