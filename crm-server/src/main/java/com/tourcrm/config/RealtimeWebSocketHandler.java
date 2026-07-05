package com.tourcrm.config;

import com.tourcrm.service.AuthService;
import com.tourcrm.service.AuthTokenSupport;
import com.tourcrm.service.RealtimeEventService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class RealtimeWebSocketHandler extends TextWebSocketHandler {

    private final AuthService authService;
    private final RealtimeEventService realtimeEventService;

    public RealtimeWebSocketHandler(AuthService authService, RealtimeEventService realtimeEventService) {
        this.authService = authService;
        this.realtimeEventService = realtimeEventService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = AuthTokenSupport.resolveFromWebSocketSession(session);
        if (!StringUtils.hasText(token)) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("missing token"));
            return;
        }
        try {
            authService.currentUser(token);
            realtimeEventService.addSession(session);
            session.sendMessage(new TextMessage("{\"type\":\"CONNECTED\",\"message\":\"connected\"}"));
        } catch (RuntimeException error) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("invalid token"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        realtimeEventService.removeSession(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        realtimeEventService.removeSession(session);
    }
}
