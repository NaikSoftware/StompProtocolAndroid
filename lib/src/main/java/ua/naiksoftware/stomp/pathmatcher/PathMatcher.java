package ua.naiksoftware.stomp.pathmatcher;

import ua.naiksoftware.stomp.TopicKey;
import ua.naiksoftware.stomp.dto.StompMessage;

public interface PathMatcher {

    boolean matches(TopicKey topicKey, StompMessage msg);
}
