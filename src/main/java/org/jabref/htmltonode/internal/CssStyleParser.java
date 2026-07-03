package org.jabref.htmltonode.internal;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.jabref.htmltonode.model.CssLength;
import org.jabref.htmltonode.model.InlineStyle;

import org.jspecify.annotations.Nullable;

/// Applies the subset of inline CSS (`style="…"` attributes) that occurs in JabRef preview HTML:
/// citeproc-java spans (`font-style`, `font-variant`, `font-weight`, `text-decoration`),
/// the search highlighter's `background`, cover-image sizing, and the AI raw template's
/// `white-space: pre-wrap`.
public final class CssStyleParser {

    private CssStyleParser() {
    }

    public static InlineStyle apply(InlineStyle style, @Nullable String styleAttribute) {
        if (styleAttribute == null || styleAttribute.isBlank()) {
            return style;
        }
        InlineStyle result = style;
        for (Map.Entry<String, String> declaration : declarations(styleAttribute).entrySet()) {
            String value = declaration.getValue();
            String lowerValue = value.toLowerCase(Locale.ROOT);
            result = switch (declaration.getKey()) {
                case "font-style" -> result.withItalic(lowerValue.startsWith("italic") || lowerValue.startsWith("oblique"));
                case "font-weight" -> result.withFontWeight(parseFontWeight(lowerValue, result.fontWeight()));
                case "font-variant", "font-variant-caps" -> result.withSmallCaps(lowerValue.contains("small-caps"));
                case "text-decoration", "text-decoration-line" -> applyTextDecoration(result, lowerValue);
                case "vertical-align" -> switch (lowerValue) {
                    case "sub" -> result.withVerticalPosition(InlineStyle.VerticalPosition.SUB);
                    case "super" -> result.withVerticalPosition(InlineStyle.VerticalPosition.SUPER);
                    case "baseline" -> result.withVerticalPosition(InlineStyle.VerticalPosition.NORMAL);
                    default -> result;
                };
                case "color" -> result.withColor(value);
                case "background", "background-color" -> result.withBackground(firstToken(value));
                case "font-size" -> parseFontSize(lowerValue, result.fontScale())
                        .map(result::withFontScale)
                        .orElse(result);
                case "font-family" -> applyFontFamily(result, value);
                default -> result;
            };
        }
        return result;
    }

    public static WhiteSpaceMode whiteSpace(@Nullable String styleAttribute, WhiteSpaceMode current) {
        if (styleAttribute == null || styleAttribute.isBlank()) {
            return current;
        }
        String value = declarations(styleAttribute).get("white-space");
        if (value == null) {
            return current;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "pre", "pre-wrap", "pre-line", "break-spaces" -> WhiteSpaceMode.PRESERVE;
            case "normal", "nowrap" -> WhiteSpaceMode.NORMAL;
            default -> current;
        };
    }

    public static boolean isDisplayBlock(@Nullable String styleAttribute) {
        if (styleAttribute == null || styleAttribute.isBlank()) {
            return false;
        }
        return "block".equalsIgnoreCase(declarations(styleAttribute).getOrDefault("display", ""));
    }

    /// Length of `property` (e.g. `height`) in a style attribute, if present and parseable.
    public static Optional<CssLength> length(@Nullable String styleAttribute, String property) {
        if (styleAttribute == null || styleAttribute.isBlank()) {
            return Optional.empty();
        }
        String value = declarations(styleAttribute).get(property);
        return value == null ? Optional.empty() : parseLength(value);
    }

    public static Optional<CssLength> parseLength(String value) {
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        try {
            if (trimmed.endsWith("px")) {
                return Optional.of(new CssLength(Double.parseDouble(trimmed.substring(0, trimmed.length() - 2).trim()), CssLength.Unit.PX));
            }
            if (trimmed.endsWith("pt")) {
                return Optional.of(new CssLength(Double.parseDouble(trimmed.substring(0, trimmed.length() - 2).trim()) * 96.0 / 72.0, CssLength.Unit.PX));
            }
            if (trimmed.endsWith("rem")) {
                return Optional.of(new CssLength(Double.parseDouble(trimmed.substring(0, trimmed.length() - 3).trim()), CssLength.Unit.EM));
            }
            if (trimmed.endsWith("em")) {
                return Optional.of(new CssLength(Double.parseDouble(trimmed.substring(0, trimmed.length() - 2).trim()), CssLength.Unit.EM));
            }
            if (trimmed.endsWith("%")) {
                return Optional.of(new CssLength(Double.parseDouble(trimmed.substring(0, trimmed.length() - 1).trim()) / 100.0, CssLength.Unit.EM));
            }
            // bare number: HTML attribute style, treat as pixels
            return Optional.of(new CssLength(Double.parseDouble(trimmed), CssLength.Unit.PX));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    static Map<String, String> declarations(String styleAttribute) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String declaration : styleAttribute.split(";")) {
            int colon = declaration.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String property = declaration.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = declaration.substring(colon + 1).trim();
            if (!property.isEmpty() && !value.isEmpty()) {
                result.put(property, value);
            }
        }
        return result;
    }

    private static InlineStyle applyTextDecoration(InlineStyle style, String lowerValue) {
        if (lowerValue.contains("none")) {
            return style.withUnderline(false).withStrikethrough(false);
        }
        InlineStyle result = style;
        if (lowerValue.contains("underline")) {
            result = result.withUnderline(true);
        }
        if (lowerValue.contains("line-through")) {
            result = result.withStrikethrough(true);
        }
        return result;
    }

    private static InlineStyle applyFontFamily(InlineStyle style, String value) {
        String first = firstToken(value.split(",")[0].trim().replace("\"", "").replace("'", ""), true);
        return switch (first.toLowerCase(Locale.ROOT)) {
            case "monospace" -> style.withMonospace(true);
            case "sans-serif", "system-ui", "cursive", "fantasy" -> style;
            case "serif" -> style.withFontFamily("Serif");
            case "" -> style;
            default -> style.withFontFamily(value.split(",")[0].trim().replace("\"", "").replace("'", ""));
        };
    }

    private static int parseFontWeight(String lowerValue, int current) {
        return switch (lowerValue) {
            case "normal" -> 400;
            case "bold" -> 700;
            case "bolder" -> current < 600 ? 700 : 900;
            case "lighter" -> current < 600 ? 100 : 400;
            default -> {
                try {
                    int weight = (int) Double.parseDouble(lowerValue);
                    yield Math.clamp(weight, 100, 900);
                } catch (NumberFormatException e) {
                    yield current;
                }
            }
        };
    }

    /// Returns the new absolute scale, `em`/`%`/`larger`/`smaller` being relative to `currentScale`.
    /// `px`/`pt` are interpreted against the 16px CSS reference size.
    private static Optional<Double> parseFontSize(String lowerValue, double currentScale) {
        Double absolute = switch (lowerValue) {
            case "xx-small" -> 0.5625;
            case "x-small" -> 0.625;
            case "small" -> 0.8125;
            case "medium" -> 1.0;
            case "large" -> 1.125;
            case "x-large" -> 1.5;
            case "xx-large" -> 2.0;
            case "xxx-large" -> 3.0;
            case "larger" -> currentScale * 1.2;
            case "smaller" -> currentScale / 1.2;
            default -> null;
        };
        if (absolute != null) {
            return Optional.of(absolute);
        }
        return parseLength(lowerValue).map(length -> switch (length.unit()) {
            case EM -> currentScale * length.value();
            case PX -> length.value() / 16.0;
        });
    }

    private static String firstToken(String value) {
        return firstToken(value, false);
    }

    private static String firstToken(String value, boolean whole) {
        String trimmed = value.trim();
        if (whole) {
            return trimmed;
        }
        int space = trimmed.indexOf(' ');
        return space < 0 ? trimmed : trimmed.substring(0, space);
    }
}
