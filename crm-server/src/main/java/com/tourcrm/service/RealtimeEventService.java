package com.tourcrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourcrm.dto.RealtimeEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
public class RealtimeEventService {

    public static final String TARGET_ASSIGN = "ASSIGN";
    public static final String TARGET_THIRD_PARTY_POOL = "THIRD_PARTY_POOL";

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper;
    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    public RealtimeEventService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void addSession(WebSocketSession session) {
        sessions.add(session);
    }

    public void removeSession(WebSocketSession session) {
        sessions.remove(session);
    }

    public void publish(String type, String customerCode, String title, String message, List<String> targets) {
        RealtimeEvent event = new RealtimeEvent(
                type,
                customerCode,
                title,
                message,
                targets == null ? List.of() : targets,
                LocalDateTime.now().format(DATE_TIME_FORMAT)
        );
        broadcast(event);
    }

    private void broadcast(RealtimeEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (IOException error) {
            return;
        }
        TextMessage message = new TextMessage(payload);
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                removeSession(session);
                continue;
            }
            try {
                session.sendMessage(message);
            } catch (IOException error) {
                removeSession(session);
            }
        }
    }
}
