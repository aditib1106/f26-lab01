package edu.cmu.cs214.booking.service;

import edu.cmu.cs214.booking.domain.Booking;
import edu.cmu.cs214.booking.domain.Room;
import edu.cmu.cs214.booking.domain.TimeInterval;
import edu.cmu.cs214.booking.domain.User;
import edu.cmu.cs214.booking.domain.WaitlistEntry;
import edu.cmu.cs214.booking.repo.BookingStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Coordinates bookings and the waitlist. Enforces the core invariant: a room
 * never holds two confirmed bookings whose intervals overlap. Persistence is
 * delegated to a {@link BookingStore}.
 */
public class BookingService {

    private final BookingStore store;
    private int nextBookingSeq = 1;
    private int nextWaitlistSeq = 1;

    public BookingService(BookingStore store) {
        this.store = store;
    }

    /**
     * Attempts to book {@code room} for {@code user} over {@code interval}. If the
     * room is free over that interval the booking is confirmed; otherwise the user
     * is placed on the room's waitlist.
     */
    public BookingResult book(Room room, User user, TimeInterval interval) {
        if (!isRoomFree(room, interval)) {
            int position = store.waitlistForRoom(room).size() + 1;
            int seq = nextWaitlistSeq++;
            store.addWaitlistEntry(new WaitlistEntry("w" + seq, room, user, interval, seq));
            return new BookingResult.Waitlisted(position);
        }
        Booking booking = new Booking("b" + nextBookingSeq++, room, user, interval);
        store.addBooking(booking);
        return new BookingResult.Confirmed(booking);
    }

    /**
     * Reports whether {@code room} is free over {@code interval}, so callers can
     * check availability before attempting to book. Consistent with {@link #book}:
     * returns {@code true} exactly when a booking for {@code interval} would be
     * confirmed rather than waitlisted.
     */
    public boolean isAvailable(Room room, TimeInterval interval) {
        return isRoomFree(room, interval);
    }

    /**
     * Cancels the confirmed booking with {@code bookingId}, freeing its slot, then
     * promotes at most one waiting user for that room.
     *
     * <p>After the slot is freed, the earliest waiter (by {@link WaitlistEntry#seq})
     * whose interval does not overlap any remaining confirmed booking for the room
     * is promoted to a confirmed booking and removed from the waitlist. Waiters that
     * still conflict are skipped in favour of later ones; if none fit, no one is
     * promoted.
     *
     * <p>Does nothing if no booking has that id.
     */
    public void cancelBooking(String bookingId) {
        Optional<Booking> found = store.findBooking(bookingId);
        if (found.isEmpty()) {
            return;
        }
        Room room = found.get().room();
        store.removeBooking(bookingId);
        promoteFromWaitlist(room);
    }

    /**
     * Promotes the earliest waiter for {@code room} whose interval is now free into
     * a confirmed booking, removing their waitlist entry. Promotes at most one and
     * never creates an overlap; if no waiter fits, the waitlist is left untouched.
     */
    private void promoteFromWaitlist(Room room) {
        List<WaitlistEntry> waiting = new ArrayList<>(store.waitlistForRoom(room));
        waiting.sort(Comparator.comparingInt(WaitlistEntry::seq));
        for (WaitlistEntry entry : waiting) {
            if (isRoomFree(room, entry.interval())) {
                Booking booking =
                    new Booking("b" + nextBookingSeq++, room, entry.user(), entry.interval());
                store.addBooking(booking);
                store.removeWaitlistEntry(entry.id());
                return;
            }
        }
    }

    /** Does {@code interval} clash with no confirmed booking currently held for {@code room}? */
    private boolean isRoomFree(Room room, TimeInterval interval) {
        for (Booking existing : store.bookingsForRoom(room)) {
            if (existing.interval().overlaps(interval)) {
                return false;
            }
        }
        return true;
    }

    /** Returns the confirmed bookings for {@code room}. */
    public List<Booking> listBookings(Room room) {
        return store.bookingsForRoom(room);
    }
}
