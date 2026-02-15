package io.github.joke;

import lombok.Value;

import java.util.List;

@Value
public class FlatTicket {
    long ticketId;
    String ticketNumber;
    long orderId;
    long orderNumber;

    Optional<TicketVenue> venue;

    List<TicketActor> actors;

    @Value
    public static class TicketActor {
        String name;
    }

    @Value
    public static class TicketVenue {
        String name;
        String street;
        String zip;
    }
}
