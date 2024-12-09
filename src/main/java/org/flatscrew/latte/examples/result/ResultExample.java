package org.flatscrew.latte.examples.result;

import org.flatscrew.latte.Command;
import org.flatscrew.latte.message.KeyPress;
import org.flatscrew.latte.Message;
import org.flatscrew.latte.Model;
import org.flatscrew.latte.Program;
import org.flatscrew.latte.Quit;
import org.flatscrew.latte.UpdateResult;

enum Choice {
    ESPRESSO("Espresso"),
    AMERICANO("Americano"),
    LATTE("Latte");

    private final String name;

    Choice(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}

public class ResultExample implements Model  {

    private int cursor;
    private String choice;

    @Override
    public Command init() {
        return null;
    }

    @Override
    public UpdateResult<? extends Model> update(Message msg) {
        if (msg instanceof KeyPress keyPress) {
            return switch (keyPress.key()) {
                case 'k', 'K', 65 -> new UpdateResult<>(this.moveUp(), null);
                case 'j', 'J', 66 -> new UpdateResult<>(this.moveDown(), null);
                case 13 -> new UpdateResult<>(this.makeChoice(), Quit::new);
                case 'q', 'Q' -> new UpdateResult<>(this, Quit::new);
                default -> new UpdateResult<>(this, null);
            };
        }
        return new UpdateResult<>(this, null);
    }

    private Model makeChoice() {
        Choice[] values = Choice.values();
        for (Choice choice : values) {
            if (choice.ordinal() == cursor) {
                this.choice =  choice.getName();
                return this;
            }
        }
        return this;
    }

    private Model moveUp() {
        if (cursor - 1 <= 0 ) {
            cursor = 0;
            return this;
        }
        cursor--;
        return this;
    }

    private Model moveDown() {
        if (cursor + 1 >= Choice.values().length) {
            cursor = 0;
            return this;
        }
        cursor++;
        return this;
    }

    @Override
    public String view() {
        StringBuilder buffer = new StringBuilder();
        buffer.append("What kind of Coffee would you like to order?\n\n");

        Choice[] values = Choice.values();
        for (int index = 0; index < values.length; index++) {
            if (cursor == index) {
                buffer.append("(•) ");
            } else {
                buffer.append("( ) ");

            }
            buffer.append(values[index].getName()).append("\n");
        }
        buffer.append("\n(press q to quit)");
        return buffer.toString();
    }

    public String getChoice() {
        return choice;
    }

    public static void main(String[] args) {
        ResultExample resultModel = new ResultExample();
        Program program = new Program(resultModel);
        program.run();

        if (resultModel.getChoice() == null) {
            return;
        }
        System.out.printf("\n---\nYou chose: %s!\n", resultModel.getChoice());
    }
}
