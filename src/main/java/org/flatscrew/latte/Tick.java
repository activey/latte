package org.flatscrew.latte;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Function;

public class Tick {
    public static Command tick(Duration duration, Function<LocalDateTime, Message> fn) {
        return () -> {
            try {
                Thread.sleep(duration.toMillis());
                return fn.apply(LocalDateTime.now());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };
    }
}