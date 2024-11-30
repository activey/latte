package org.flatscrew.latte;

public interface Renderer {

    void start();
    void stop();
    void write(String view);
    void clearScreen();
    void showCursor();
    void hideCursor();

    void notifyModelChanged();
}
