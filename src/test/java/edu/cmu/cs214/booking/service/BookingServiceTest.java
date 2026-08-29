package edu.cmu.cs214.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import edu.cmu.cs214.booking.domain.Booking;
import edu.cmu.cs214.booking.domain.Room;
import edu.cmu.cs214.booking.domain.TimeInterval;
import edu.cmu.cs214.booking.domain.User;
import edu.cmu.cs214.booking.repo.InMemoryBookingStore;
import java.util.List;
import org.junit.jupiter.api.Test;

class BookingServiceTest {

    private final Room roomA = new Room("A", "Alpha", 10);
    private final Room roomB = new Room("B", "Beta", 4);
    private final User alice = new User("u1", "Alice");
    private final User bob = new User("u2", "Bob");
    private final User carol = new User("u3", "Carol");
    private final User dan = new User("u4", "Dan");

    private BookingService newService() {
        return new BookingService(new InMemoryBookingStore());
    }

    @Test
    void bookConfirmsWhenRoomIsFree() {
        BookingService svc = newService();
        BookingResult r = svc.book(roomA, alice, new TimeInterval(600, 660));
        assertInstanceOf(BookingResult.Confirmed.class, r);
    }

    @Test
    void bookWaitlistsWhenSlotIsTaken() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 660));
        BookingResult r = svc.book(roomA, bob, new TimeInterval(630, 700));
        assertInstanceOf(BookingResult.Waitlisted.class, r);
    }

    @Test
    void backToBackBookingsAreConfirmed() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 660));
        BookingResult r = svc.book(roomA, bob, new TimeInterval(660, 720));
        assertInstanceOf(BookingResult.Confirmed.class, r);
    }

    @Test
    void sameSlotInDifferentRoomsAreBothConfirmed() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 660));
        BookingResult r = svc.book(roomB, bob, new TimeInterval(600, 660));
        assertInstanceOf(BookingResult.Confirmed.class, r);
    }

    @Test
    void listBookingsReturnsConfirmedBookings() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 660));
        svc.book(roomA, bob, new TimeInterval(660, 720));
        assertEquals(2, svc.listBookings(roomA).size());
    }

    @Test
    void cancelRemovesTheBooking() {
        BookingService svc = newService();
        BookingResult r = svc.book(roomA, alice, new TimeInterval(600, 660));
        String id = ((BookingResult.Confirmed) r).booking().id();
        svc.cancelBooking(id);
        assertEquals(0, svc.listBookings(roomA).size());
    }

    @Test
    void cancelUnknownIdIsNoOp() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 660));
        svc.cancelBooking("no-such-id");
        assertEquals(1, svc.listBookings(roomA).size());
    }

    @Test
    void cancelPromotesWaitingUser() {
        BookingService svc = newService();
        BookingResult first = svc.book(roomA, alice, new TimeInterval(600, 660));
        BookingResult second = svc.book(roomA, bob, new TimeInterval(630, 700));
        assertInstanceOf(BookingResult.Waitlisted.class, second);

        svc.cancelBooking(((BookingResult.Confirmed) first).booking().id());

        List<Booking> bookings = svc.listBookings(roomA);
        assertEquals(1, bookings.size());
        assertEquals(bob, bookings.get(0).user());
        assertEquals(new TimeInterval(630, 700), bookings.get(0).interval());
    }

    @Test
    void cancelSkipsWaiterThatStillConflicts() {
        BookingService svc = newService();
        BookingResult early = svc.book(roomA, alice, new TimeInterval(600, 660));
        svc.book(roomA, dan, new TimeInterval(700, 760));
        // bob (seq 1) waits for a slot overlapping both confirmed bookings;
        // carol (seq 2) overlaps only alice's.
        svc.book(roomA, bob, new TimeInterval(630, 720));
        svc.book(roomA, carol, new TimeInterval(600, 660));

        svc.cancelBooking(((BookingResult.Confirmed) early).booking().id());

        List<Booking> bookings = svc.listBookings(roomA);
        assertEquals(2, bookings.size());
        assertEquals(1, bookings.stream().filter(b -> b.user().equals(carol)).count());
        assertEquals(0, bookings.stream().filter(b -> b.user().equals(bob)).count());
    }

    @Test
    void cancelPromotesNoOneWhenNoWaiterFits() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 700));
        BookingResult later = svc.book(roomA, dan, new TimeInterval(700, 800));
        svc.book(roomA, bob, new TimeInterval(650, 750)); // overlaps both bookings

        svc.cancelBooking(((BookingResult.Confirmed) later).booking().id());

        List<Booking> bookings = svc.listBookings(roomA);
        assertEquals(1, bookings.size());
        assertEquals(alice, bookings.get(0).user());
    }

    @Test
    void isAvailableIsFalseWhenAnEarlierBookingOverlaps() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 720));
        // The room is held 10:00-12:00; a 10:30-11:40 slot is not available.
        assertFalse(svc.isAvailable(roomA, new TimeInterval(630, 700)));
    }

    @Test
    void isAvailableAgreesWithBook() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 720));
        TimeInterval slot = new TimeInterval(630, 700);
        // If isAvailable says the slot is free, booking it must confirm, not waitlist.
        if (svc.isAvailable(roomA, slot)) {
            assertInstanceOf(BookingResult.Confirmed.class, svc.book(roomA, bob, slot));
        }
    }
}
