package io.github.joke;

import lombok.Value;

import java.util.List;

@Value
public class Ticket {
    long ticketId;
    String ticketNumber;

    List<Actor> actors;
}
