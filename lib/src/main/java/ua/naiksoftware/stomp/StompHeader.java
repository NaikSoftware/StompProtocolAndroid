package ua.naiksoftware.stomp;

/**
 * Created by naik on 05.05.16.
 */
public class StompHeader {

    public static final String VERSION = "version";
    public static final String HEART_BEAT = "heart-beat";
    public static final String DESTINATION = "destination";
    public static final String CONTENT_TYPE = "content-type";
    public static final String MESSAGE_ID = "message-id";
    public static final String ID = "id";
    public static final String ACK = "ack";

    private final String mKey;
    private final String mValue;

    public StompHeader(String key, String value) {
        mKey = key;
        mValue = value;
    }

    public String getKey() {
        return mKey;
    }

    public String getValue() {
        return mValue;
    }
}
