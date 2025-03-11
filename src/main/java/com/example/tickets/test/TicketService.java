package com.example.tickets.test;

import com.example.tickets.test.domain.TicketTypeRequest;
import com.example.tickets.test.exception.InvalidPurchaseException;

public interface TicketService {

    void purchaseTickets(Long accountId, TicketTypeRequest... ticketTypeRequests) throws InvalidPurchaseException;

}
