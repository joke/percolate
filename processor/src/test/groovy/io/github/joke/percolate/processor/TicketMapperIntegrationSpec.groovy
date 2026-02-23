package io.github.joke.percolate.processor

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class TicketMapperIntegrationSpec extends Specification {

    def "generates TicketMapperImpl with full mapping pipeline"() {
        given:
        def actor = JavaFileObjects.forSourceLines('io.github.joke.Actor',
            'package io.github.joke;',
            'public class Actor {',
            '    private final String firstName;',
            '    private final String lastName;',
            '    public Actor(String firstName, String lastName) {',
            '        this.firstName = firstName;',
            '        this.lastName = lastName;',
            '    }',
            '    public String getFirstName() { return firstName; }',
            '    public String getLastName() { return lastName; }',
            '}',
        )

        def venue = JavaFileObjects.forSourceLines('io.github.joke.Venue',
            'package io.github.joke;',
            'public class Venue {',
            '    private final String name;',
            '    private final String street;',
            '    private final String zipCode;',
            '    private final String city;',
            '    public Venue(String name, String street, String zipCode, String city) {',
            '        this.name = name;',
            '        this.street = street;',
            '        this.zipCode = zipCode;',
            '        this.city = city;',
            '    }',
            '    public String getName() { return name; }',
            '    public String getStreet() { return street; }',
            '    public String getZipCode() { return zipCode; }',
            '    public String getCity() { return city; }',
            '}',
        )

        def order = JavaFileObjects.forSourceLines('io.github.joke.Order',
            'package io.github.joke;',
            'public class Order {',
            '    private final long orderId;',
            '    private final long orderNumber;',
            '    private final Venue venue;',
            '    public Order(long orderId, long orderNumber, Venue venue) {',
            '        this.orderId = orderId;',
            '        this.orderNumber = orderNumber;',
            '        this.venue = venue;',
            '    }',
            '    public long getOrderId() { return orderId; }',
            '    public long getOrderNumber() { return orderNumber; }',
            '    public Venue getVenue() { return venue; }',
            '}',
        )

        def ticket = JavaFileObjects.forSourceLines('io.github.joke.Ticket',
            'package io.github.joke;',
            'import java.util.List;',
            'public class Ticket {',
            '    private final long ticketId;',
            '    private final String ticketNumber;',
            '    private final List<Actor> actors;',
            '    public Ticket(long ticketId, String ticketNumber, List<Actor> actors) {',
            '        this.ticketId = ticketId;',
            '        this.ticketNumber = ticketNumber;',
            '        this.actors = actors;',
            '    }',
            '    public long getTicketId() { return ticketId; }',
            '    public String getTicketNumber() { return ticketNumber; }',
            '    public List<Actor> getActors() { return actors; }',
            '}',
        )

        def flatTicket = JavaFileObjects.forSourceLines('io.github.joke.FlatTicket',
            'package io.github.joke;',
            'import java.util.List;',
            'import java.util.Optional;',
            'public class FlatTicket {',
            '    private final long ticketId;',
            '    private final String ticketNumber;',
            '    private final long orderId;',
            '    private final long orderNumber;',
            '    private final Optional<FlatTicket.TicketVenue> venue;',
            '    private final List<FlatTicket.TicketActor> actors;',
            '    public FlatTicket(long ticketId, String ticketNumber, long orderId, long orderNumber, Optional<FlatTicket.TicketVenue> venue, List<FlatTicket.TicketActor> actors) {',
            '        this.ticketId = ticketId;',
            '        this.ticketNumber = ticketNumber;',
            '        this.orderId = orderId;',
            '        this.orderNumber = orderNumber;',
            '        this.venue = venue;',
            '        this.actors = actors;',
            '    }',
            '    public static class TicketActor {',
            '        private final String name;',
            '        public TicketActor(String name) { this.name = name; }',
            '        public String getName() { return name; }',
            '    }',
            '    public static class TicketVenue {',
            '        private final String name;',
            '        private final String street;',
            '        private final String zip;',
            '        public TicketVenue(String name, String street, String zip) {',
            '            this.name = name;',
            '            this.street = street;',
            '            this.zip = zip;',
            '        }',
            '        public String getName() { return name; }',
            '        public String getStreet() { return street; }',
            '        public String getZip() { return zip; }',
            '    }',
            '}',
        )

        def ticketMapper = JavaFileObjects.forSourceLines('io.github.joke.TicketMapper',
            'package io.github.joke;',
            'import io.github.joke.percolate.Mapper;',
            'import io.github.joke.percolate.Map;',
            '@Mapper',
            'public interface TicketMapper {',
            '    @Map(target = "ticketId", source = "ticket.ticketId")',
            '    @Map(target = "ticketNumber", source = "ticket.ticketNumber")',
            '    @Map(target = "actors", source = "ticket.actors")',
            '    @Map(target = ".", source = "order.*")',
            '    FlatTicket mapPerson(Ticket ticket, Order order);',
            '',
            '    @Map(target = "zip", source = "zipCode")',
            '    FlatTicket.TicketVenue mapVenue(Venue venue);',
            '',
            '    default FlatTicket.TicketActor mapActor(Actor actor) {',
            '        String name = actor.getFirstName() + " " + actor.getLastName();',
            '        return new FlatTicket.TicketActor(name);',
            '    }',
            '}',
        )

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(actor, venue, order, ticket, flatTicket, ticketMapper)

        then:
        assertThat(compilation).succeeded()

        and: 'generates TicketMapperImpl'
        assertThat(compilation)
            .generatedSourceFile('io.github.joke.TicketMapperImpl')

        and: 'verify generated source content'
        def impl = compilation.generatedSourceFile('io.github.joke.TicketMapperImpl')
            .get().getCharContent(false).toString()

        // mapPerson uses ticket parameters
        impl.contains('ticket.getTicketId()')
        impl.contains('ticket.getTicketNumber()')

        // wildcard expansion for order
        impl.contains('order.getOrderId()')
        impl.contains('order.getOrderNumber()')

        // actors mapped via stream with default method reference
        impl.contains('.stream()')
        impl.contains('this::mapActor') || impl.contains('this.mapActor(')
        impl.contains('collect(')

        // venue wrapped in Optional with mapVenue
        impl.contains('Optional.ofNullable')
        impl.contains('mapVenue')

        // mapVenue maps zipCode to zip
        impl.contains('venue.getZipCode()')

        // mapActor is NOT generated (default method)
        !impl.contains('public FlatTicket.TicketActor mapActor')
    }
}
