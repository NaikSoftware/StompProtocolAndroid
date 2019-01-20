package ua.naiksoftware.stomp.pathmatcher;

import ua.naiksoftware.stomp.dto.StompMessage;

public interface PathMatcher {

    boolean matches(String path, StompMessage msg);
}
