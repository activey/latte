package org.flatscrew.latte;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
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

    private void startKeyboardInput() {
        Thread inputThread = new Thread(() -> {
            try {
                var reader = terminal.reader();
                while (isRunning.get()) {
                    int input = reader.read();
                    send(new KeyPress(input));
                }
            } catch (IOException e) {
                if (isRunning.get()) {
                    e.printStackTrace();
                }
            }
        });
        inputThread.setDaemon(true);
        inputThread.start();
    }

    private void clearScreen() {
        terminal.puts(InfoCmp.Capability.cursor_home);   // Move cursor to top-left
        terminal.puts(InfoCmp.Capability.clr_eos);       // Clear from cursor to end of screen
        terminal.flush();
    }

    public void run() {
        if (!isRunning.compareAndSet(false, true)) {
            throw new IllegalStateException("Latte is already running");
        }

        Cmd initCmd = currentModel.init();
        if (initCmd != null) {
            cmdExecutor.submit(() -> initCmd.execute(this));
        }

        initTerminal();
        startKeyboardInput();
        renderer.start();

        while (isRunning.get()) {
            try {
                Message msg = messageQueue.poll(50, TimeUnit.MILLISECONDS);
                if (msg != null) {
                    UpdateResult<? extends Model> updateResult = currentModel.update(msg);
                    currentModel = updateResult.model();
                    renderer.notifyModelChanged();
                    if (updateResult.cmd() != null) {
                        cmdExecutor.submit(() -> updateResult.cmd().execute(this));
                    }
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            renderer.write(currentModel.view());
        }

        shutdown();
    }

    private void shutdown() {
        renderer.showCursor();
    }

    private void initTerminal() {
        renderer.hideCursor();
    }

    public void send(Message msg) {
        if (isRunning.get()) {
            messageQueue.offer(msg);
        }
    }

    public void quit() {
        isRunning.set(false);
        cmdExecutor.shutdown();
    }
}
