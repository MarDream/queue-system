package com.queue.listener;

import com.queue.service.TicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * 监听 Redis 票号过期 Key，到期自动标记为过号
 */
@Component
public class TicketExpirationListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(TicketExpirationListener.class);
    private static final String TICKET_EXPIRE_KEY_PREFIX = "ticket:expire:";

    private final TicketService ticketService;

    public TicketExpirationListener(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = message.toString();
        if (expiredKey.startsWith(TICKET_EXPIRE_KEY_PREFIX)) {
            String ticketIdStr = expiredKey.substring(TICKET_EXPIRE_KEY_PREFIX.length());
            try {
                Long ticketId = Long.parseLong(ticketIdStr);
                ticketService.markAsSkipped(ticketId);
                log.info("票号 {} 已到期，自动标记为过号", ticketId);
            } catch (Exception e) {
                log.error("处理票号过期事件失败: {}", expiredKey, e);
            }
        }
    }
}
