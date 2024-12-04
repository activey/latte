package org.flatscrew.latte.examples.result;

import org.flatscrew.latte.Command;
import org.flatscrew.latte.KeyPress;
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

class ResultModel implements Model {

    private int cursor;
    private String choice;

    public ResultModel() {}

    public ResultModel(int cursor) {
        this.cursor = cursor;
    }

    public ResultModel(int cursor, String choice) {
        this.cursor = cursor;
        this.choice = choice;
    }

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
                return new ResultModel(cursor, choice.getName());
            }
        }
        return this;
    }

    private Model moveUp() {
        if (cursor - 1 <= 0 ) {
            return new ResultModel(0);
        }
        return new ResultModel(cursor - 1);
    }

    private Model moveDown() {
        if (cursor + 1 >= Choice.values().length) {
            return new ResultModel(0);
        }
        return new ResultModel(cursor + 1);
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
}

public class ResultExample {

    public static void main(String[] args) {
        Program program = new Program(new ResultModel());
        program.run();

        if (program.getCurrentModel() instanceof ResultModel resultModel && resultModel.getChoice() != null) {
            System.out.printf("\n---\nYou chose: %s!\n", resultModel.getChoice());
        }
    }
}
