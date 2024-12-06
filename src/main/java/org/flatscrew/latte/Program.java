package org.flatscrew.latte;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class Program {

    private final Renderer renderer;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final BlockingQueue<Message> messageQueue = new LinkedBlockingQueue<>();
    private final ExecutorService cmdExecutor = Executors.newCachedThreadPool();
    private volatile Model currentModel;

    private final Terminal terminal;

    public Program(Model initialModel) {
        this.currentModel = initialModel;

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
                var reader = terminal.reader();
                while (isRunning.get()) {
                    int input = reader.read();
                    send(new KeyPress(input));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        inputThread.setDaemon(true);
        inputThread.start();
    }

    public void run() {
        Model model = eventLoop();

        renderer.write(model.view());
        renderer.stop();
        renderer.showCursor();

        terminal.puts(InfoCmp.Capability.carriage_return);
        terminal.puts(InfoCmp.Capability.cursor_down);
        terminal.flush();

        // Finally clean up
        shutdown();
    }

    private Model eventLoop() {
        if (!isRunning.compareAndSet(false, true)) {
            throw new IllegalStateException("Latte is already running");
        }

        Command initCommand = currentModel.init();
        if (initCommand != null) {
            CompletableFuture
                    .supplyAsync(initCommand::execute, cmdExecutor)
                    .thenAccept(this::send)
                    .exceptionally(ex -> {
                        ex.printStackTrace();
                        return null;
                    });
        }

        initTerminal();
        startKeyboardInput();
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
                    }

                    UpdateResult<? extends Model> updateResult = currentModel.update(msg);

                    currentModel = updateResult.model();
                    renderer.notifyModelChanged();


                    if (updateResult.command() != null) {
                        CompletableFuture
                                .supplyAsync(() -> updateResult.command().execute(), cmdExecutor)
                                .thenAccept(this::send)
                                .exceptionally(ex -> {
                                    ex.printStackTrace();
                                    return null;
                                });
                    }
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            renderer.write(currentModel.view());
        }

        return currentModel;
    }

    private void shutdown() {
        isRunning.set(false);
        cmdExecutor.shutdown();
    }

    private void initTerminal() {
        renderer.hideCursor();
    }

    public void send(Message msg) {
        if (isRunning.get()) {
            messageQueue.offer(msg);
        }
    }
}
