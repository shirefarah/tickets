package com.example.tickets.thirdparty.seatbooking;

public interface SeatReservationService {

    void reserveSeat(long accountId, int totalSeatsToAllocate);

}