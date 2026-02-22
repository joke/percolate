package io.github.joke;

import java.util.List;
import lombok.Value;

@Value
public class Ticket {
    long ticketId;
    String ticketNumber;

    List<Actor> actors;
}
