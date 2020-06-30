package ua.naiksoftware.stomp.pathmatcher;

import ua.naiksoftware.stomp.TopicKey;
import ua.naiksoftware.stomp.dto.StompHeader;
import ua.naiksoftware.stomp.dto.StompMessage;

public class SimplePathMatcher implements PathMatcher {

    @Override
    public boolean matches(TopicKey topicKey, StompMessage msg) {
        String dest = msg.findHeader(StompHeader.DESTINATION);
        if (dest == null) return false;
        else return topicKey.destination.equals(dest);
    }
}
