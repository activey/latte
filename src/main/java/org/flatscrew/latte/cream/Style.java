package org.flatscrew.latte.cream;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

public class Style {

    private AttributedStyle style = new AttributedStyle();

    public Style foreground(Color color) {
        this.style = style.foreground(color.code());
        return this;
    }

    public Style background(Color color) {
        this.style = style.background(color.code());
        return this;
    }

    public String render(String... strings) {
        return new AttributedString(String.join(" ", strings), style).toAnsi();
    }
}
