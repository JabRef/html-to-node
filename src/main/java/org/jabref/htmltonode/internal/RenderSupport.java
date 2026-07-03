package org.jabref.htmltonode.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javafx.scene.paint.Color;

import org.jabref.htmltonode.model.InlineStyle;

/// Rendering helpers shared by the TextFlow-based and the RichTextArea-based renderer.
public final class RenderSupport {

    /// Font scale per heading level (`<h1>`–`<h6>`), mirroring common browser defaults.
    private static final double[] HEADING_SCALE = {2.0, 1.5, 1.17, 1.0, 0.83, 0.67};

    private RenderSupport() {
    }

    public static double headingScale(int level) {
        return HEADING_SCALE[level - 1];
    }

    /// A run fragment with a relative size factor (used for small-caps emulation).
    public record TextPiece(String text, double sizeFactor) {
    }

    /// Small-caps emulation: lowercase stretches are rendered as smaller capitals.
    public static List<TextPiece> splitForSmallCaps(String text, InlineStyle style) {
        if (!style.smallCaps() || text.isEmpty()) {
            return List.of(new TextPiece(text, 1.0));
        }
        List<TextPiece> pieces = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean currentLower = Character.isLowerCase(text.charAt(0));
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean lower = Character.isLowerCase(c);
            if (lower != currentLower) {
                pieces.add(piece(current, currentLower));
                current.setLength(0);
                currentLower = lower;
            }
            current.append(c);
        }
        pieces.add(piece(current, currentLower));
        return pieces;
    }

    private static TextPiece piece(StringBuilder text, boolean lower) {
        String value = text.toString();
        return lower
                ? new TextPiece(value.toUpperCase(Locale.ROOT), 0.8)
                : new TextPiece(value, 1.0);
    }

    public static Optional<Color> parseColor(String value) {
        try {
            return Optional.of(Color.web(value.trim()));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    public static String toCssColor(Color color) {
        return String.format(Locale.ROOT, "rgba(%d,%d,%d,%.3f)",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255),
                color.getOpacity());
    }
}
