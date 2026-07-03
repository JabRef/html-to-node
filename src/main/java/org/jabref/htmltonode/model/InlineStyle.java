package org.jabref.htmltonode.model;

import org.jspecify.annotations.Nullable;

/// Resolved (absolute) styling of an inline run.
///
/// Parsers resolve inheritance and resets while walking the DOM (e.g. `<span style="font-style: normal">`
/// inside `<i>` yields `italic = false`), so instances always carry final values and renderers
/// never need to consult ancestors.
///
/// @param fontWeight       CSS font weight, 100–900; 400 is normal, 700 is bold
/// @param italic           italic or oblique
/// @param underline        underlined text
/// @param strikethrough    struck-through text
/// @param smallCaps        `font-variant: small-caps` (renderers emulate via scaled capitals)
/// @param monospace        code/tt/kbd/samp or `font-family: monospace`
/// @param verticalPosition subscript/superscript placement
/// @param fontScale        font size relative to the renderer's base size; 1.0 = base
/// @param fontFamily       explicit font family, or `null` to use the renderer default
/// @param color            CSS color string (e.g. `#ff0000`, `orange`), or `null` for the default
/// @param background       CSS background color string (e.g. from `<mark>`), or `null` for none
/// @param href             link target; non-`null` means this run is a hyperlink
public record InlineStyle(
        int fontWeight,
        boolean italic,
        boolean underline,
        boolean strikethrough,
        boolean smallCaps,
        boolean monospace,
        VerticalPosition verticalPosition,
        double fontScale,
        @Nullable String fontFamily,
        @Nullable String color,
        @Nullable String background,
        @Nullable String href) {

    /// Vertical placement of a run relative to the baseline.
    public enum VerticalPosition {
        /// On the baseline.
        NORMAL,
        /// Subscript (`<sub>`, `vertical-align: sub`).
        SUB,
        /// Superscript (`<sup>`, `vertical-align: super`).
        SUPER
    }

    /// The style of unmarked body text: weight 400, base size, no decorations, default fonts and colors.
    public static final InlineStyle DEFAULT = new InlineStyle(
            400, false, false, false, false, false, VerticalPosition.NORMAL, 1.0, null, null, null, null);

    /// @return whether the [#fontWeight()] renders bold (600 or heavier)
    public boolean bold() {
        return fontWeight >= 600;
    }

    /// @return whether this run is a hyperlink ([#href()] is set)
    public boolean link() {
        return href != null;
    }

    public InlineStyle withFontWeight(int newFontWeight) {
        return new InlineStyle(newFontWeight, italic, underline, strikethrough, smallCaps, monospace, verticalPosition, fontScale, fontFamily, color, background, href);
    }

    public InlineStyle withItalic(boolean newItalic) {
        return new InlineStyle(fontWeight, newItalic, underline, strikethrough, smallCaps, monospace, verticalPosition, fontScale, fontFamily, color, background, href);
    }

    public InlineStyle withUnderline(boolean newUnderline) {
        return new InlineStyle(fontWeight, italic, newUnderline, strikethrough, smallCaps, monospace, verticalPosition, fontScale, fontFamily, color, background, href);
    }

    public InlineStyle withStrikethrough(boolean newStrikethrough) {
        return new InlineStyle(fontWeight, italic, underline, newStrikethrough, smallCaps, monospace, verticalPosition, fontScale, fontFamily, color, background, href);
    }

    public InlineStyle withSmallCaps(boolean newSmallCaps) {
        return new InlineStyle(fontWeight, italic, underline, strikethrough, newSmallCaps, monospace, verticalPosition, fontScale, fontFamily, color, background, href);
    }

    public InlineStyle withMonospace(boolean newMonospace) {
        return new InlineStyle(fontWeight, italic, underline, strikethrough, smallCaps, newMonospace, verticalPosition, fontScale, fontFamily, color, background, href);
    }

    public InlineStyle withVerticalPosition(VerticalPosition newVerticalPosition) {
        return new InlineStyle(fontWeight, italic, underline, strikethrough, smallCaps, monospace, newVerticalPosition, fontScale, fontFamily, color, background, href);
    }

    public InlineStyle withFontScale(double newFontScale) {
        return new InlineStyle(fontWeight, italic, underline, strikethrough, smallCaps, monospace, verticalPosition, newFontScale, fontFamily, color, background, href);
    }

    public InlineStyle withFontFamily(@Nullable String newFontFamily) {
        return new InlineStyle(fontWeight, italic, underline, strikethrough, smallCaps, monospace, verticalPosition, fontScale, newFontFamily, color, background, href);
    }

    public InlineStyle withColor(@Nullable String newColor) {
        return new InlineStyle(fontWeight, italic, underline, strikethrough, smallCaps, monospace, verticalPosition, fontScale, fontFamily, newColor, background, href);
    }

    public InlineStyle withBackground(@Nullable String newBackground) {
        return new InlineStyle(fontWeight, italic, underline, strikethrough, smallCaps, monospace, verticalPosition, fontScale, fontFamily, color, newBackground, href);
    }

    public InlineStyle withHref(@Nullable String newHref) {
        return new InlineStyle(fontWeight, italic, underline, strikethrough, smallCaps, monospace, verticalPosition, fontScale, fontFamily, color, background, newHref);
    }
}
