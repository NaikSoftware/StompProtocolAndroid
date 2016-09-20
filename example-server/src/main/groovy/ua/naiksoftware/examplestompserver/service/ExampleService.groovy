package ua.naiksoftware.examplestompserver.service

import org.springframework.stereotype.Service
import ua.naiksoftware.examplestompserver.UserPrincipal

/**
 * Created by naik on 18.09.16.
 */
@Service
class ExampleService {
    
    static String SESSION_ATTR_ROOM_ID = 'attr_room_id'
    
    def removeUserFromSpectatorsRegistry(long userId, String sessionId) {
        
    }

    def getAllHistoryForRoom(long roomId, String sessionId, UserPrincipal userPrincipal) {
        
    }
}
