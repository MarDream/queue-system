package com.queue.scheduler;

import com.queue.service.TicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 票号定时任务：每日凌晨扫描并标记历史未办结票为过号
 */
@Component
public class TicketScheduler {

    private static final Logger log = LoggerFactory.getLogger(TicketScheduler.class);

    private final TicketService ticketService;

    public TicketScheduler(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    /**
     * 每日凌晨 00:05 执行，扫描并标记所有历史未办结票为过号
     * 作为 Redis 过期策略的兜底
     */
    @Scheduled(cron = "0 5 0 * * ?")
    public void markExpiredTicketsOnMidnight() {
        log.info("【定时任务】开始执行历史未办结票扫描...");
        try {
            int count = ticketService.markExpiredTickets();
            log.info("【定时任务】历史未办结票扫描完成，共处理 {} 张票", count);
        } catch (Exception e) {
            log.error("【定时任务】历史未办结票扫描异常", e);
        }
    }
}
