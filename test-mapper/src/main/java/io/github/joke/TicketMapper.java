package io.github.joke;

import io.github.joke.FlatTicket.TicketActor;
import io.github.joke.FlatTicket.TicketVenue;
import io.github.joke.caffeinate.Map;
import io.github.joke.caffeinate.Mapper;
import java.util.List;

@Mapper
public interface TicketMapper {

    @Map(target = "ticketId", source = "ticket.ticketId")
    @Map(target = "ticketNumber", source = "ticket.ticketNumber")
    // map all matching properties beneath order onto the target FlatTicket
    @Map(target = ".", source = "order.*")
    FlatTicket mapPerson(Ticket ticket, Order order);

    // all identical properties are mapped automatically
    @Map(target = "zip", source = "zipCode")
    TicketVenue mapVenue(Venue venue);

    List<TicketActor> mapTicketActors(List<TicketActor> source);

    default TicketActor mapActor(Actor actor) {
        final var name = actor.getFirstName() + " " + actor.getLastName();
        return new TicketActor(name);
    }
}
