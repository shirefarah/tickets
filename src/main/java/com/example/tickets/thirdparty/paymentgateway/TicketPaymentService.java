package com.example.tickets.thirdparty.paymentgateway;

public interface TicketPaymentService {

    void makePayment(long accountId, int totalAmountToPay);

}
