package com.bbms.config;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/** Adjustable clock used only by the isolated training profile to simulate elapsed appointment time. */
public final class TrainingClock extends Clock {
    private final AtomicReference<Instant> current;
    private final ZoneId zone;

    public TrainingClock(ZoneId zone) {
        this(Instant.now(), zone);
    }

    private TrainingClock(Instant instant, ZoneId zone) {
        this.current = new AtomicReference<>(instant);
        this.zone = zone;
    }

    public void advanceTo(LocalDateTime dateTime) {
        Instant next = dateTime.atZone(zone).toInstant();
        current.updateAndGet(existing -> next.isAfter(existing) ? next : existing);
    }

    @Override public ZoneId getZone() { return zone; }
    @Override public Clock withZone(ZoneId zone) { return new TrainingClock(instant(), zone); }
    @Override public Instant instant() { return current.get(); }
}
