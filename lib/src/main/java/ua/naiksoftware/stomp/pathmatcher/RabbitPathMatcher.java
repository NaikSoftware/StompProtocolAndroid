package ua.naiksoftware.stomp.pathmatcher;

import ua.naiksoftware.stomp.dto.StompHeader;
import ua.naiksoftware.stomp.dto.StompMessage;

import java.util.ArrayList;

public class RabbitPathMatcher implements PathMatcher {

    /**
     * RMQ-style wildcards.
     * See more info <a href="https://www.rabbitmq.com/tutorials/tutorial-five-java.html">here</a>.
     */
    @Override
    public boolean matches(String path, StompMessage msg) {
        String dest = msg.findHeader(StompHeader.DESTINATION);
        if (dest == null) return false;

        // for example string "lorem.ipsum.*.sit":

        // split it up into ["lorem", "ipsum", "*", "sit"]
        String[] split = path.split("\\.");
        ArrayList<String> transformed = new ArrayList<>();
        // check for wildcards and replace with corresponding regex
        for (String s : split) {
            switch (s) {
                case "*":
                    transformed.add("[^.]+");
                    break;
                case "#":
                    // TODO: make this work with zero-word
                    // e.g. "lorem.#.dolor" should ideally match "lorem.dolor"
                    transformed.add(".*");
                    break;
                default:
                    transformed.add(s.replaceAll("\\*", ".*"));
                    break;
            }
        }
        // at this point, 'transformed' looks like ["lorem", "ipsum", "[^.]+", "sit"]
        StringBuilder sb = new StringBuilder();
        for (String s : transformed) {
            if (sb.length() > 0) sb.append("\\.");
            sb.append(s);
        }
        String join = sb.toString();
        // join = "lorem\.ipsum\.[^.]+\.sit"

        return dest.matches(join);
    }
}
