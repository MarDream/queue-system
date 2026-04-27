package com.queue.service;

import com.queue.dto.AiAskRequest;
import com.queue.dto.AiAskResponse;
import com.queue.dto.AiMessageDTO;
import com.queue.dto.AiSessionDTO;

import java.util.List;
import java.util.Map;

public interface AiQueryService {
    AiAskResponse ask(Long userId, AiAskRequest request);

    List<AiSessionDTO> listSessions(Long userId, String workspace);

    List<AiMessageDTO> getMessages(Long userId, String workspace, String sessionId);

    void deleteSession(Long userId, String workspace, String sessionId);

    Map<String, Object> getLastTable(Long userId, String workspace, String sessionId);
}
