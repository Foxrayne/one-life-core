package io.pzstorm.launcher.ui;

import java.awt.Font;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;

/** User-facing One/Life artwork kept separate from Storm's internal compatibility names. */
final class OneLifeBrand {

    private static final BufferedImage ICON = load("one-life-icon.png");
    private static final BufferedImage BANNER = load("one-life-banner.png");

    private OneLifeBrand() {}

    static Image iconImage() {
        return ICON;
    }

    static JLabel headerLabel() {
        if (BANNER != null) {
            return new JLabel(new ImageIcon(BANNER));
        }
        JLabel fallback = new JLabel("ONE/LIFE CORE");
        fallback.setFont(StormTheme.displayFont(Font.BOLD, 20f));
        fallback.setForeground(StormTheme.ACCENT);
        return fallback;
    }

    private static BufferedImage load(String name) {
        try (InputStream in = OneLifeBrand.class.getResourceAsStream(name)) {
            return in == null ? null : ImageIO.read(in);
        } catch (Exception ignored) {
            return null;
        }
    }
}
