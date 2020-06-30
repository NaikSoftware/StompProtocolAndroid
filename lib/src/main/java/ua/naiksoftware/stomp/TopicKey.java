package ua.naiksoftware.stomp;

import java.util.List;
import ua.naiksoftware.stomp.dto.StompHeader;

public final class TopicKey {
    public final String destination;
    public final List<StompHeader> headerList;

    public TopicKey(String destPath, List<StompHeader> headerList) {
        this.destination = destPath;
        this.headerList = headerList;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TopicKey topicKey = (TopicKey) o;

        if (!destination.equals(topicKey.destination)) return false;
        return headerList != null ? headerList.equals(topicKey.headerList) : topicKey.headerList == null;
    }

    @Override
    public int hashCode() {
        int result = destination.hashCode();
        result = 31 * result + (headerList != null ? headerList.hashCode() : 0);
        return result;
    }
}
