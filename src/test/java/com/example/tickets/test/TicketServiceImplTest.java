package com.example.tickets.test;

import com.example.tickets.test.domain.TicketTypeRequest;
import com.example.tickets.test.exception.InvalidPurchaseException;
import com.example.tickets.thirdparty.paymentgateway.TicketPaymentService;
import com.example.tickets.thirdparty.seatbooking.SeatReservationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TicketServiceImplTest {

    @Mock
    private SeatReservationService seatReservationService;

    @Mock
    private TicketPaymentService ticketPaymentService;

    @InjectMocks
    private TicketServiceImpl ticketService;

    // Helper method to create TicketTypeRequest instances.
    private TicketTypeRequest createTicketRequest(TicketTypeRequest.Type type, int noOfTickets) {
        return new TicketTypeRequest(type, noOfTickets);
    }

    @Test
    @DisplayName("Successful purchase with valid tickets")
    public void testPurchaseTickets_withValidTickets() throws Exception {
        long accountId = 1L;
        TicketTypeRequest adultRequest = createTicketRequest(TicketTypeRequest.Type.ADULT, 2);
        TicketTypeRequest childRequest = createTicketRequest(TicketTypeRequest.Type.CHILD, 1);
        TicketTypeRequest infantRequest = createTicketRequest(TicketTypeRequest.Type.INFANT, 1);

        TicketTypeRequest[] requests = new TicketTypeRequest[] { adultRequest, childRequest, infantRequest };

        assertDoesNotThrow(() -> ticketService.purchaseTickets(accountId, requests));

        verify(ticketPaymentService, times(1)).makePayment(accountId, 50);
        verify(seatReservationService, times(1)).reserveSeat(accountId, 3);
    }

    @Test
    @DisplayName("Fails when account ID is null")
    public void testPurchaseTickets_invalidAccountId_null() {
        TicketTypeRequest adultRequest = createTicketRequest(TicketTypeRequest.Type.ADULT, 1);
        TicketTypeRequest[] requests = new TicketTypeRequest[] { adultRequest };

        Exception exception = assertThrows(InvalidPurchaseException.class, () ->
                ticketService.purchaseTickets(null, requests)
        );
        assertTrue(exception.getMessage().contains("Account id must be valid"));
    }

    @Test
    @DisplayName("Fails when account ID is negative")
    public void testPurchaseTickets_invalidAccountId_negative() {
        TicketTypeRequest adultRequest = createTicketRequest(TicketTypeRequest.Type.ADULT, 1);
        TicketTypeRequest[] requests = new TicketTypeRequest[] { adultRequest };

        Exception exception = assertThrows(InvalidPurchaseException.class, () ->
                ticketService.purchaseTickets(-1L, requests)
        );
        assertTrue(exception.getMessage().contains("Account id must be valid"));
    }

    @Test
    @DisplayName("Fails when no ticket types are requested")
    public void testPurchaseTickets_noTicketTypesRequested() {
        Exception exception = assertThrows(InvalidPurchaseException.class, () ->
                ticketService.purchaseTickets(1L)
        );
        assertTrue(exception.getMessage().contains("At least one ticket type must be requested"));
    }

    @Test
    @DisplayName("Fails when a ticket request has a negative quantity")
    public void testPurchaseTickets_negativeTicketQuantity() {
        TicketTypeRequest adultRequest = createTicketRequest(TicketTypeRequest.Type.ADULT, -1);
        TicketTypeRequest[] requests = new TicketTypeRequest[] { adultRequest };

        Exception exception = assertThrows(InvalidPurchaseException.class, () ->
                ticketService.purchaseTickets(1L, requests)
        );
        assertTrue(exception.getMessage().contains("Ticket quantity must be positive"));
    }

    @Test
    @DisplayName("Fails when a null ticket request is included")
    public void testPurchaseTickets_nullTicketRequest() {
        TicketTypeRequest adultRequest = createTicketRequest(TicketTypeRequest.Type.ADULT, 1);
        TicketTypeRequest[] requests = new TicketTypeRequest[] { adultRequest, null};

        Exception exception = assertThrows(InvalidPurchaseException.class, () ->
                ticketService.purchaseTickets(1L, requests)
        );
        assertTrue(exception.getMessage().contains("Ticket request must not be null"));
    }

    @Test
    @DisplayName("Fails when child or infant tickets are requested without any adult ticket")
    public void testPurchaseTickets_noAdultTicketForChildOrInfant() {
        TicketTypeRequest childRequest = createTicketRequest(TicketTypeRequest.Type.CHILD, 1);
        TicketTypeRequest infantRequest = createTicketRequest(TicketTypeRequest.Type.INFANT, 1);
        TicketTypeRequest[] requests = new TicketTypeRequest[] { childRequest, infantRequest };

        Exception exception = assertThrows(InvalidPurchaseException.class, () ->
                ticketService.purchaseTickets(1L, requests)
        );
        assertTrue(exception.getMessage().contains("Adult ticket must be purchased for child or infant"));
    }

    @Test
    @DisplayName("Fails when total tickets exceed the maximum allowed per purchase")
    public void testPurchaseTickets_totalTicketsExceedLimit() {
        // Request 21 adult tickets (limit is 20)
        TicketTypeRequest adultRequest = createTicketRequest(TicketTypeRequest.Type.ADULT, 26);
        TicketTypeRequest[] requests = new TicketTypeRequest[] { adultRequest };

        Exception exception = assertThrows(InvalidPurchaseException.class, () ->
                ticketService.purchaseTickets(1L, requests)
        );
        assertTrue(exception.getMessage().contains("Total tickets must not exceed"));
    }

    @Test
    @DisplayName("Fails when infant tickets exceed adult tickets")
    public void testPurchaseTickets_infantTicketsMoreThanAdult() {
        TicketTypeRequest adultRequest = createTicketRequest(TicketTypeRequest.Type.ADULT, 1);
        TicketTypeRequest infantRequest = createTicketRequest(TicketTypeRequest.Type.INFANT, 2);
        TicketTypeRequest[] requests = new TicketTypeRequest[] { adultRequest, infantRequest };

        Exception exception = assertThrows(InvalidPurchaseException.class, () ->
                ticketService.purchaseTickets(1L, requests)
        );
        assertTrue(exception.getMessage().contains("Infant tickets cannot be more than adult tickets"));
    }

    @Test
    @DisplayName("Fails when payment service throws an exception")
    public void testPurchaseTickets_paymentFails() throws Exception {
        Long accountId = 1L;
        TicketTypeRequest adultRequest = createTicketRequest(TicketTypeRequest.Type.ADULT, 1);
        TicketTypeRequest[] requests = new TicketTypeRequest[] { adultRequest };

        doThrow(new RuntimeException("Payment error"))
                .when(ticketPaymentService).makePayment(accountId, 20);

        Exception exception = assertThrows(InvalidPurchaseException.class, () ->
                ticketService.purchaseTickets(accountId, requests)
        );
        assertTrue(exception.getMessage().contains("Payment failed"));
        // Ensure seat reservation is never called if payment fails.
        verify(seatReservationService, never()).reserveSeat(anyLong(), anyInt());
    }

    @Test
    @DisplayName("Fails when seat reservation service throws an exception")
    public void testPurchaseTickets_seatReservationFails() throws Exception {
        Long accountId = 1L;
        TicketTypeRequest adultRequest = createTicketRequest(TicketTypeRequest.Type.ADULT, 1);
        TicketTypeRequest[] requests = new TicketTypeRequest[] { adultRequest };

        // Simulate a successful payment
        doNothing().when(ticketPaymentService).makePayment(accountId, 20);
        // Simulate failure in seat reservation
        doThrow(new RuntimeException("Seat reservation error"))
                .when(seatReservationService).reserveSeat(accountId, 1);

        Exception exception = assertThrows(InvalidPurchaseException.class, () ->
                ticketService.purchaseTickets(accountId, requests)
        );
        assertTrue(exception.getMessage().contains("Seat reservation failed"));
    }
}