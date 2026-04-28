package com.queue.scheduler;

import com.queue.service.TicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 票号定时任务：在后台周期性清理历史未办结票
 */
@Component
public class TicketScheduler {

    private static final Logger log = LoggerFactory.getLogger(TicketScheduler.class);

    private final TicketService ticketService;

    public TicketScheduler(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Scheduled(cron = "0 5 0 * * ?")
    public void markExpiredTicketsOnMidnight() {
        runExpiredTicketSweep("midnight");
    }

    @Scheduled(initialDelay = 300000, fixedDelay = 3600000)
    public void reconcileExpiredTicketsPeriodically() {
        runExpiredTicketSweep("hourly");
    }

    private void runExpiredTicketSweep(String source) {
        log.info("【定时任务】开始执行历史未办结票清理，source={}", source);
        try {
            int count = ticketService.markExpiredTickets();
            log.info("【定时任务】历史未办结票清理完成，source={}，共处理 {} 张票", source, count);
        } catch (Exception e) {
            log.error("【定时任务】历史未办结票清理异常，source={}", source, e);
        }
    }
}
