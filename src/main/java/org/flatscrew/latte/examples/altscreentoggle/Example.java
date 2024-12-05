package org.flatscrew.latte.examples.altscreentoggle;

import org.flatscrew.latte.Command;
import org.flatscrew.latte.EnterAltScreen;
import org.flatscrew.latte.ExitAltScreen;
import org.flatscrew.latte.KeyPress;
import org.flatscrew.latte.Message;
import org.flatscrew.latte.Model;
import org.flatscrew.latte.Program;
import org.flatscrew.latte.Quit;
import org.flatscrew.latte.UpdateResult;

enum Mode {
    ALT_SCREEN("altscreen mode"),
    INLINE("inline mode");

    private final String description;

    Mode(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

class ExampleModel implements Model {

    private boolean altscreen;
    private boolean quitting;
    private boolean suspending;

    @Override
    public Command init() {
        return null;
    }

    @Override
    public UpdateResult<? extends Model> update(Message msg) {
        if (msg instanceof KeyPress keyPress) {
            return switch (keyPress.key()) {
                case 'q', 'Q' -> {
                    quitting = true;
                    yield new UpdateResult<>(this, Quit::new);
                }
                case ' ' -> {
                    Command cmd;
                    if (altscreen) {
                        cmd = ExitAltScreen::new;
                    } else {
                        cmd = EnterAltScreen::new;
                    }
                    altscreen = !altscreen;
                    yield new UpdateResult<>(this, cmd);
                }
                default -> new UpdateResult<>(this, null);
            };
        }

        return null;
    }

    @Override
    public String view() {
        if (suspending) {
            return "";
        }
        if (quitting) {
            return "Bye!\n";
        }

        Mode mode;
        if (altscreen) {
            mode = Mode.ALT_SCREEN;
        } else {
            mode = Mode.INLINE;
        }

        return "\n\n You're in %s\n\n\n".formatted(mode.getDescription()) +
                "  space: switch modes • ctrl-z: suspend • q: exit\n";
    }
}

public class Example {

    public static void main(String[] args) {
        Program program = new Program(new ExampleModel());
        program.run();
    }
}
