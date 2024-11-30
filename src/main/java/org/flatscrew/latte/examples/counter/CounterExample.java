package org.flatscrew.latte.examples.counter;

import org.flatscrew.latte.*;
import org.flatscrew.latte.Program;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

enum CounterMsg implements Message {
    INCREMENT, DECREMENT, INCREMENT_LATER
}

class CounterModel implements Model {

    private final int value;

    public CounterModel(int value) {
        this.value = value;
    }

    @Override
    public Cmd init() {
        return null;
    }

    @Override
    public UpdateResult<? extends Model> update(Message msg) {
        if (msg instanceof KeyPress keyPress) {
            return switch (keyPress.key()) {
                case 'k', 'K', 65 -> new UpdateResult<>(this, app -> app.send(CounterMsg.INCREMENT));
                case 'j', 'J', 66 -> new UpdateResult<>(this, app -> app.send(CounterMsg.DECREMENT));
                case 'd', 'D' -> new UpdateResult<>(this, app -> app.send(CounterMsg.INCREMENT_LATER));
                case 'q', 'Q' -> new UpdateResult<>(this, Program::quit);
                default -> new UpdateResult<>(this, null);
            };
        } else if (msg == CounterMsg.INCREMENT) {
            return UpdateResult.of(increment());
        } else if (msg == CounterMsg.DECREMENT) {
            return UpdateResult.of(decrement());
        } else if (msg == CounterMsg.INCREMENT_LATER) {
            return new UpdateResult<>(this, app -> {
                CompletableFuture
                        .delayedExecutor(1, TimeUnit.SECONDS)
                        .execute(() -> app.send(CounterMsg.INCREMENT));
            });
        }
        return new UpdateResult<>(this, null);
    }

    public CounterModel increment() {
        return new CounterModel(value + 1);
    }

    public CounterModel decrement() {
        return new CounterModel(value - 1);
    }

    @Override
    public String view() {
        return """
                Counter Example
                ==============
                              \s
                Value: %d
                              \s
                Commands:
                ↑/k - Increment
                ↓/j - Decrement
                d - Delayed Increment
                q - Quit
               \s""".formatted(value);
    }
}

public class CounterExample {

    public static void main(String[] args) {
        Program app = new Program(new CounterModel(0));
        app.run();
    }
}
