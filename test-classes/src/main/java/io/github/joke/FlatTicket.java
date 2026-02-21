package io.github.joke;

import java.util.List;
import lombok.Value;

@Value
public class FlatTicket {
    long ticketId;
    String ticketNumber;
    long orderId;
    long orderNumber;

    List<TicketActor> ticketActors;

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
