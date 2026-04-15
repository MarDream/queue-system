package com.queue.service;

import com.queue.dto.ActiveTicketResponse;
import com.queue.dto.AdminTicketVO;
import com.queue.dto.CancelTicketRequest;
import com.queue.dto.MyTicketVO;
import com.queue.dto.QueueStatusResponse;
import com.queue.dto.TakeTicketRequest;
import com.queue.dto.TakeTicketResponse;

import java.util.List;
import java.util.Set;

public interface TicketService {
    TakeTicketResponse takeTicket(TakeTicketRequest request);
    QueueStatusResponse getQueueStatus(String ticketNo);
    void cancelTicket(CancelTicketRequest request);
    List<MyTicketVO> getMyTickets(String phone);
    ActiveTicketResponse getActiveTicket(Long regionId, String phone);
    List<AdminTicketVO> listTickets(String status, String date, String startDate, String endDate, String phone, String name, String ticketNo);
    List<AdminTicketVO> listTickets(String status, String date, String startDate, String endDate, String phone, String name, String ticketNo, Set<Long> allowedRegionIds);
}
