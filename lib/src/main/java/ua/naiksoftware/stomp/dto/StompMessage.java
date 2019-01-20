package ua.naiksoftware.stomp.dto;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by naik on 05.05.16.
 */
public class StompMessage {

    public static final String TERMINATE_MESSAGE_SYMBOL = "\u0000";

    private static final Pattern PATTERN_HEADER = Pattern.compile("([^:\\s]+)\\s*:\\s*([^:\\s]+)");

    private final String mStompCommand;
    private final List<StompHeader> mStompHeaders;
    private final String mPayload;

    public StompMessage(String stompCommand, List<StompHeader> stompHeaders, String payload) {
        mStompCommand = stompCommand;
        mStompHeaders = stompHeaders;
        mPayload = payload;
    }

    public List<StompHeader> getStompHeaders() {
        return mStompHeaders;
    }

    public String getPayload() {
        return mPayload;
    }

    public String getStompCommand() {
        return mStompCommand;
    }

    @Nullable
    public String findHeader(String key) {
        if (mStompHeaders == null) return null;
        for (StompHeader header : mStompHeaders) {
            if (header.getKey().equals(key)) return header.getValue();
        }
        return null;
    }

    @NonNull
    public String compile() {
        return compile(false);
    }

    @NonNull
    public String compile(boolean legacyWhitespace) {
        StringBuilder builder = new StringBuilder();
        builder.append(mStompCommand).append('\n');
        for (StompHeader header : mStompHeaders) {
            builder.append(header.getKey()).append(':').append(header.getValue()).append('\n');
        }
        builder.append('\n');
        if (mPayload != null) {
            builder.append(mPayload);
            if (legacyWhitespace) builder.append("\n\n");
        }
        builder.append(TERMINATE_MESSAGE_SYMBOL);
        return builder.toString();
    }

    public static StompMessage from(@Nullable String data) {
        if (data == null || data.trim().isEmpty()) {
            return new StompMessage(StompCommand.UNKNOWN, null, data);
        }
        Scanner reader = new Scanner(new StringReader(data));
        reader.useDelimiter("\\n");
        String command = reader.next();
        List<StompHeader> headers = new ArrayList<>();

        while (reader.hasNext(PATTERN_HEADER)) {
            Matcher matcher = PATTERN_HEADER.matcher(reader.next());
            matcher.find();
            headers.add(new StompHeader(matcher.group(1), matcher.group(2)));
        }

        reader.skip("\\s");

        reader.useDelimiter(TERMINATE_MESSAGE_SYMBOL);
        String payload = reader.hasNext() ? reader.next() : null;

        return new StompMessage(command, headers, payload);
    }

    @Override
    public String toString() {
        return "StompMessage{" +
                "command='" + mStompCommand + '\'' +
                ", headers=" + mStompHeaders +
                ", payload='" + mPayload + '\'' +
                '}';
    }
}
