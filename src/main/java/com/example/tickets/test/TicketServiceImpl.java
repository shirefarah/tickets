package com.example.tickets.test;

import com.example.tickets.test.domain.TicketTypeRequest;
import com.example.tickets.test.exception.InvalidPurchaseException;
import com.example.tickets.thirdparty.paymentgateway.TicketPaymentService;
import com.example.tickets.thirdparty.seatbooking.SeatReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class TicketServiceImpl implements TicketService {

    private final SeatReservationService seatReservationService;
    private final TicketPaymentService ticketPaymentService;

    private static final int MAX_TICKETS_PER_PURCHASE = 25;
    private static final int PRICE_ADULT = 20;
    private static final int PRICE_CHILD = 10;
    private static final int PRICE_INFANT = 0;

    private static final Logger logger = LoggerFactory.getLogger(TicketServiceImpl.class);

    public TicketServiceImpl(SeatReservationService seatReservationService, TicketPaymentService ticketPaymentService) {
        this.seatReservationService = seatReservationService;
        this.ticketPaymentService = ticketPaymentService;
    }

    @Override
    public void purchaseTickets(Long accountId, TicketTypeRequest... ticketTypeRequests) throws InvalidPurchaseException {
        logger.info("Initiating ticket purchase for account: {} with {} ticket requested", accountId, ticketTypeRequests != null ? ticketTypeRequests.length : 0);

        validatePurchase(accountId, ticketTypeRequests);

        Map<TicketTypeRequest.Type, Integer> ticketTypeCount = countTicketType(ticketTypeRequests);

        int adultTickets = ticketTypeCount.getOrDefault(TicketTypeRequest.Type.ADULT, 0);
        int childTickets = ticketTypeCount.getOrDefault(TicketTypeRequest.Type.CHILD, 0);
        int infantTickets = ticketTypeCount.getOrDefault(TicketTypeRequest.Type.INFANT, 0);

        logger.debug("Aggregated ticket counts => Adult: {}, Child: {}, Infant: {}", adultTickets, childTickets, infantTickets);

        checkBusinessRules(accountId, adultTickets, childTickets, infantTickets);

        int totalTickets = adultTickets + childTickets + infantTickets;
        int totalSeatsToReserve = adultTickets + childTickets;
        int totalAmountToPay = calculateTotalAmount(adultTickets, childTickets, infantTickets);

        logger.debug("Total tickets: {}, Total seats to reserve: {}, Total amount to pay: {}", totalTickets, totalSeatsToReserve, totalAmountToPay);

        processPayment(accountId, totalAmountToPay);
        reserveSeats(accountId, totalSeatsToReserve);

        logger.info("Ticket purchase successful for account: {} (Adult: {}, Child: {}, Infant: {})", accountId, adultTickets, childTickets, infantTickets);
    }

    private Map<TicketTypeRequest.Type, Integer> countTicketType(TicketTypeRequest[] ticketTypeRequests) {
        Map<TicketTypeRequest.Type, Integer> ticketTypeCount = new HashMap<>();
        if (ticketTypeRequests != null) {
            for (TicketTypeRequest request : ticketTypeRequests) {
                TicketTypeRequest.Type type = request.getTicketType();
                int count = ticketTypeCount.getOrDefault(type, 0);
                ticketTypeCount.put(type, count + request.getNoOfTickets());
            }
        }
        return ticketTypeCount;
    }

    private void validatePurchase(Long accountId, TicketTypeRequest[] ticketTypeRequests) {
        if (accountId == null || accountId <= 0) {
            logger.error("Invalid account id: {}", accountId);
            throw new InvalidPurchaseException("Account id must be valid");
        }
        if (ticketTypeRequests == null || ticketTypeRequests.length == 0) {
            logger.error("No ticket type requested for account: {}", accountId);
            throw new InvalidPurchaseException("At least one ticket type must be requested");
        }

        int totalTickets = 0;
        for (TicketTypeRequest request : ticketTypeRequests) {
            if (request == null) {
                logger.error("Null ticket type request for account: {}", accountId);
                throw new InvalidPurchaseException("Ticket request must not be null");
            }
            int quantity = request.getNoOfTickets();
            if (quantity < 0) {
                logger.error("Negative ticket quantity requested for account: {}", accountId);
                throw new InvalidPurchaseException("Ticket quantity must be positive");
            }
            totalTickets += quantity;
        }
        if (totalTickets == 0) {
            logger.error("No ticket quantity requested for account: {}", accountId);
            throw new InvalidPurchaseException("At least one ticket must be requested");
        }
    }

    private void checkBusinessRules(Long accountId, int adultTickets, int childTickets, int infantTickets) {
        if ((childTickets > 0 || infantTickets > 0) && adultTickets == 0) {
            logger.error("Invalid ticket purchase for account: {} (Adult: {}, Child: {}, Infant: {})", accountId, adultTickets, childTickets, infantTickets);
            throw new InvalidPurchaseException("Adult ticket must be purchased for child or infant");
        }

        int totalTickets = adultTickets + childTickets + infantTickets;

        if (totalTickets > MAX_TICKETS_PER_PURCHASE) {
            logger.error("Invalid ticket purchase for account: {} (Total tickets: {})", accountId, totalTickets);
            throw new InvalidPurchaseException("Total tickets must not exceed " + MAX_TICKETS_PER_PURCHASE);
        }

        if (infantTickets > adultTickets) {//added this rule to check if infant tickets are more than adult tickets even though it is not required
            logger.error("Account {}: Infants ({}) cannot be more than adults ({})", accountId, infantTickets, adultTickets);
            throw new InvalidPurchaseException("Infant tickets cannot be more than adult tickets");
        }
    }

    private int calculateTotalAmount(int adultTickets, int childTickets, int infantTickets) {
        return adultTickets * PRICE_ADULT + childTickets * PRICE_CHILD + infantTickets * PRICE_INFANT;
    }

    private void processPayment(Long accountId, int totalAmountToPay) throws InvalidPurchaseException {
        try {
            ticketPaymentService.makePayment(accountId, totalAmountToPay);
            logger.info("Payment successful for account: {} (charged ${})", accountId, totalAmountToPay);
        } catch (Exception e) {
            logger.error("Payment service error for account: {}: {}", accountId, e.getMessage(), e);
            throw new InvalidPurchaseException("Payment failed: " + e.getMessage());
        }
    }

    private void reserveSeats(Long accountId, int totalSeatsToReserve) throws InvalidPurchaseException {
        try {
            seatReservationService.reserveSeat(accountId, totalSeatsToReserve);
            logger.info("Seat reservation successful for account: {} (reserved {} seats)", accountId, totalSeatsToReserve);
        } catch (Exception e) {
            logger.error("Seat reservation service error for account: {}: {}", accountId, e.getMessage(), e);
            throw new InvalidPurchaseException("Seat reservation failed: " + e.getMessage());
        }
    }
}