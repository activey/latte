package org.flatscrew.latte;

import org.jline.terminal.Terminal;
import org.jline.utils.InfoCmp;

import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class StandardRenderer implements Renderer {

    private static final int DEFAULT_FPS = 60;

    private volatile boolean needsRender = true;
    private final Lock renderLock = new ReentrantLock();
    private final Terminal terminal;
    private volatile boolean isRunning = false;
    private final StringBuilder buffer = new StringBuilder();
    private volatile String lastRender = "";
    private final ScheduledExecutorService ticker;
    private final long frameTime;
    private String[] lastRenderedLines = new String[0];
    private int linesRendered = 0;
    private int width = 0;
    private int height = 0;

    public StandardRenderer(Terminal terminal) {
        this(terminal, DEFAULT_FPS);
    }

    public StandardRenderer(Terminal terminal, int fps) {
        this.terminal = terminal;
        this.frameTime = 1000 / Math.min(Math.max(fps, 1), 120);
        this.ticker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Renderer-Thread");
            t.setDaemon(true);
            return t;
        });

        try {
            // Get terminal size
            this.width = terminal.getWidth();
            this.height = terminal.getHeight();
        } catch (Exception e) {
            // Fallback to some reasonable defaults if we can't get the size
            this.width = 80;
            this.height = 24;
        }
    }

    public void start() {
        if (!isRunning) {
            isRunning = true;
            ticker.scheduleAtFixedRate(this::flush, 0, frameTime, TimeUnit.MILLISECONDS);
        }
    }

    public void stop() {
        isRunning = false;
        ticker.shutdown();
        try {
            ticker.awaitTermination(frameTime * 2, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void flush() {
        renderLock.lock();
        try {
            if (buffer.isEmpty() || buffer.toString().equals(lastRender)) {
                return;
            }

            StringBuilder outputBuffer = new StringBuilder();
            String[] newLines = buffer.toString().split("\n");

            // If height is known and content exceeds it, trim from top
            if (height > 0 && newLines.length > height) {
                newLines = Arrays.copyOfRange(newLines, newLines.length - height, newLines.length);
            }

            // Move cursor to start of render area
            if (linesRendered > 1) {
                outputBuffer.append("\033[").append(linesRendered - 1).append("A");
            }

            // Paint new lines
            for (int i = 0; i < newLines.length; i++) {
                boolean canSkip = lastRenderedLines.length > i &&
                        newLines[i].equals(lastRenderedLines[i]);

                if (canSkip) {
                    if (i < newLines.length - 1) {
                        outputBuffer.append("\033[B"); // Move down one line
                    }
                    continue;
                }

                // Clear line and write new content
                outputBuffer.append("\r\033[K").append(newLines[i]);

                if (i < newLines.length - 1) {
                    outputBuffer.append("\n");
                }
            }

            // Clear any remaining lines from previous render
            if (linesRendered > newLines.length) {
                outputBuffer.append("\033[J"); // Clear screen below
            }

            // Ensure cursor is at the start of the last line
            outputBuffer.append("\r");

            terminal.writer().print(outputBuffer);
            terminal.writer().flush();

            lastRender = buffer.toString();
            lastRenderedLines = newLines;
            linesRendered = newLines.length;
            buffer.setLength(0);
        } finally {
            renderLock.unlock();
        }
    }

    public void write(String view) {
        if (!isRunning || !needsRender) return;

        renderLock.lock();
        try {
            buffer.setLength(0);  // Clear existing buffer
            buffer.append(view);
        } finally {
            renderLock.unlock();
            needsRender = false;
        }
    }

    @Override
    public void clearScreen() {
        renderLock.lock();
        try {
            terminal.puts(InfoCmp.Capability.clear_screen);
            terminal.flush();
            lastRender = "";
            buffer.setLength(0);
        } finally {
            renderLock.unlock();
        }
    }

    @Override
    public void showCursor() {
        renderLock.lock();
        try {
            terminal.writer().print("\033[?25h"); // ANSI escape code to show cursor
            terminal.writer().flush();
        } finally {
            renderLock.unlock();
        }
    }

    @Override
    public void hideCursor() {
        renderLock.lock();
        try {
            terminal.writer().print("\033[?25l"); // ANSI escape code to hide cursor
            terminal.writer().flush();
        } finally {
            renderLock.unlock();
        }
    }

    @Override
    public void notifyModelChanged() {
        this.needsRender = true;
    }
}