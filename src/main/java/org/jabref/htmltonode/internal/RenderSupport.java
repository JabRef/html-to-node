package org.jabref.htmltonode.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;

import org.jabref.htmltonode.HtmlRenderOptions;
import org.jabref.htmltonode.model.CssLength;
import org.jabref.htmltonode.model.Inline;
import org.jabref.htmltonode.model.InlineStyle;

import org.jspecify.annotations.Nullable;

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

    /// Creates a sized [ImageView] for an `<img>`, or `null` when images are disabled or the
    /// source scheme is not allowed (`http(s):` requires [HtmlRenderOptions#loadRemoteImages()]).
    public static @Nullable ImageView createImageView(Inline.Image image, HtmlRenderOptions options, double baseSize) {
        if (!options.renderImages() || !allowedImageSource(image.source(), options)) {
            return null;
        }
        ImageView view = new ImageView();
        view.getStyleClass().add("html-image");
        view.setPreserveRatio(true);
        view.setSmooth(true);
        CssLength width = image.width();
        CssLength height = image.height();
        if (width != null) {
            view.setFitWidth(width.toPixels(baseSize));
        }
        if (height != null) {
            view.setFitHeight(height.toPixels(baseSize));
        }
        if (width != null && height != null) {
            view.setPreserveRatio(false);
        }
        try {
            view.setImage(new Image(image.source(), true));
        } catch (RuntimeException | LinkageError e) {
            // bad URI, or image subsystem unavailable (e.g. fully headless tests):
            // keep the sized, empty view
        }
        return view;
    }

    private static boolean allowedImageSource(String source, HtmlRenderOptions options) {
        String lower = source.toLowerCase(Locale.ROOT);
        if (lower.startsWith("file:") || lower.startsWith("data:") || lower.startsWith("jar:")) {
            return true;
        }
        return options.loadRemoteImages() && (lower.startsWith("http:") || lower.startsWith("https:"));
    }

    public static String toCssColor(Color color) {
        return String.format(Locale.ROOT, "rgba(%d,%d,%d,%.3f)",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255),
                color.getOpacity());
    }
}
