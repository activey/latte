package org.flatscrew.latte;

import org.flatscrew.latte.message.*;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp;
import org.jline.utils.NonBlockingReader;
import org.jline.utils.Signals;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class Program {

    private final Renderer renderer;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final BlockingQueue<Message> messageQueue = new LinkedBlockingQueue<>();
    private final CommandExecutor commandExecutor;
    private volatile Model currentModel;

    private final Terminal terminal;

    public Program(Model initialModel) {
        this.currentModel = initialModel;
        this.commandExecutor = new CommandExecutor();

        try {
            this.terminal = TerminalBuilder.builder()
                    .system(true)
                    .jni(true)
                    .build();
            terminal.enterRawMode();

            this.renderer = new StandardRenderer(terminal);
        } catch (IOException e) {
            System.err.println("Failed to initialize terminal: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize terminal", e);
        }
    }

    public Program withAltScreen() {
        renderer.enterAltScreen();
        return this;
    }

    private void startKeyboardInput() {
        Thread inputThread = new Thread(() -> {
            try {
                NonBlockingReader reader = terminal.reader();
                while (isRunning.get()) {
                    send(new KeyPress(reader.read()));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        inputThread.setDaemon(true);
        inputThread.start();
    }

    public void run() {
        if (!isRunning.compareAndSet(false, true)) {
            throw new IllegalStateException("Program is already running!");
        }

        startKeyboardInput();
        handleTerminationSignals();

        Model finalModel = eventLoop();

        // render final model view before closing
        renderer.write(finalModel.view());
        renderer.stop();
        renderer.showCursor();

        if (renderer.altScreen()) {
            renderer.exitAltScreen();
        }

        terminal.puts(InfoCmp.Capability.carriage_return);
        terminal.puts(InfoCmp.Capability.cursor_down);
        terminal.flush();

        // Finally clean up
        isRunning.set(false);
        commandExecutor.shutdown();
    }

    private void handleTerminationSignals() {
        Signals.register("INT", () -> send(new QuitMessage()));
        Signals.register("TERM", () -> send(new QuitMessage()));
    }

    private Model eventLoop() {
        Command initCommand = currentModel.init();
        commandExecutor.executeIfPresent(initCommand, this::send);

        renderer.hideCursor();
        renderer.start();

        while (isRunning.get()) {
            try {
                Message msg = messageQueue.poll(50, TimeUnit.MILLISECONDS);
                if (msg != null) {
                    if (msg instanceof Quit) {
                        return currentModel;
                    } else if (msg instanceof EnterAltScreen) {
                        renderer.enterAltScreen();
                        continue;
                    } else if (msg instanceof ExitAltScreen) {
                        renderer.exitAltScreen();
                        continue;
                    } else if (msg instanceof QuitMessage) {
                        return currentModel;
                    } else if (msg instanceof BatchMessage batchMessage) {
                        for (Command command : batchMessage.commands()) {
                            commandExecutor.executeIfPresent(command, this::send);
                        }
                    }

                    UpdateResult<? extends Model> updateResult = currentModel.update(msg);

                    currentModel = updateResult.model();
                    renderer.notifyModelChanged();
                    commandExecutor.executeIfPresent(updateResult.command(), this::send);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            renderer.write(currentModel.view());
        }
        return currentModel;
    }

    public void send(Message msg) {
        if (isRunning.get()) {
            messageQueue.offer(msg);
        }
    }
}
