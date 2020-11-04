package ua.naiksoftware.stomp.pathmatcher;

import ua.naiksoftware.stomp.StompClient;
import ua.naiksoftware.stomp.dto.StompHeader;
import ua.naiksoftware.stomp.dto.StompHeader.*;
import ua.naiksoftware.stomp.dto.StompMessage;

public class SubscriptionPathMatcher implements PathMatcher {

    private final  StompClient stompClient;

    public SubscriptionPathMatcher(StompClient stompClient) {
        this.stompClient = stompClient;
    }

    @Override
    public boolean matches(String path, StompMessage msg) {
       // Compare subscription
        String pathSubscription = stompClient.getTopicId(path);
        if (pathSubscription == null) return false;
        String subscription = msg.findHeader(StompHeader.SUBSCRIPTION);
        return pathSubscription.equals(subscription);
    }
}
