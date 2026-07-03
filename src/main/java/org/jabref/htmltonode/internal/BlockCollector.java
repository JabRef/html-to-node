package org.jabref.htmltonode.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.jabref.htmltonode.model.Block;
import org.jabref.htmltonode.model.Inline;
import org.jabref.htmltonode.model.InlineStyle;

/// Accumulates blocks while walking the DOM. Inline content between block elements is gathered
/// into "anonymous" paragraphs (`spaced = false`, no CSS classes), with HTML whitespace collapsing
/// across element boundaries. U+00A0 (`&nbsp;`) is intentionally not treated as collapsible.
final class BlockCollector {

    private static final Pattern COLLAPSIBLE_WHITESPACE = Pattern.compile("[ \\t\\n\\r\\f\\x0B]+");

    private final List<Block> blocks = new ArrayList<>();
    private final List<Inline> currentInlines = new ArrayList<>();
    private final boolean preserveWhitespace;

    /// true at paragraph start and after emitted whitespace/line breaks → leading spaces are dropped
    private boolean afterWhitespace = true;

    BlockCollector(boolean preserveWhitespace) {
        this.preserveWhitespace = preserveWhitespace;
    }

    void appendText(String text, InlineStyle style, WhiteSpaceMode whiteSpace) {
        if (text.isEmpty()) {
            return;
        }
        if (whiteSpace == WhiteSpaceMode.PRESERVE) {
            String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
            currentInlines.add(new Inline.TextRun(normalized, style));
            afterWhitespace = false;
            return;
        }
        String collapsed = COLLAPSIBLE_WHITESPACE.matcher(text).replaceAll(" ");
        if (afterWhitespace && collapsed.startsWith(" ")) {
            collapsed = collapsed.substring(1);
        }
        if (collapsed.isEmpty()) {
            return;
        }
        currentInlines.add(new Inline.TextRun(collapsed, style));
        afterWhitespace = collapsed.endsWith(" ");
    }

    void appendLineBreak() {
        currentInlines.add(new Inline.LineBreak());
        afterWhitespace = true;
    }

    void appendImage(Inline.Image image) {
        if (image.blockImage()) {
            closeParagraph();
            blocks.add(new Block.Paragraph(List.of(image), false, List.of()));
            return;
        }
        currentInlines.add(image);
        afterWhitespace = false;
    }

    /// Adds a block, closing any open anonymous paragraph first.
    void add(Block block) {
        closeParagraph();
        blocks.add(block);
    }

    List<Block> finish() {
        closeParagraph();
        return blocks;
    }

    private void closeParagraph() {
        if (currentInlines.isEmpty()) {
            afterWhitespace = true;
            return;
        }
        List<Inline> inlines = mergeAdjacentRuns(currentInlines);
        currentInlines.clear();
        afterWhitespace = true;

        if (!preserveWhitespace) {
            boolean hasContent = inlines.stream().anyMatch(inline -> switch (inline) {
                case Inline.TextRun(String text, InlineStyle ignored) -> !text.isBlank();
                case Inline.Image ignored -> true;
                case Inline.LineBreak ignored -> false;
            });
            if (!hasContent) {
                // "<div><br></div>" renders as an empty line; pure inter-block whitespace does not
                if (inlines.stream().anyMatch(Inline.LineBreak.class::isInstance)) {
                    blocks.add(new Block.Paragraph(List.of(new Inline.LineBreak()), false, List.of()));
                }
                return;
            }
            inlines = trimTrailing(inlines);
        }
        if (!inlines.isEmpty()) {
            blocks.add(new Block.Paragraph(List.copyOf(inlines), false, List.of()));
        }
    }

    /// Removes trailing whitespace runs and trailing line breaks (browsers do not render
    /// a line break that is the last thing in a block).
    private static List<Inline> trimTrailing(List<Inline> inlines) {
        List<Inline> result = new ArrayList<>(inlines);
        while (!result.isEmpty()) {
            Inline last = result.getLast();
            if (last instanceof Inline.LineBreak) {
                result.removeLast();
            } else if (last instanceof Inline.TextRun(String text, InlineStyle style)) {
                String trimmed = text.stripTrailing();
                if (trimmed.isEmpty()) {
                    result.removeLast();
                } else {
                    if (trimmed.length() != text.length()) {
                        result.set(result.size() - 1, new Inline.TextRun(trimmed, style));
                    }
                    break;
                }
            } else {
                break;
            }
        }
        return result;
    }

    private static List<Inline> mergeAdjacentRuns(List<Inline> inlines) {
        List<Inline> result = new ArrayList<>(inlines.size());
        for (Inline inline : inlines) {
            if (inline instanceof Inline.TextRun(String text, InlineStyle style)
                    && !result.isEmpty()
                    && result.getLast() instanceof Inline.TextRun(String lastText, InlineStyle lastStyle)
                    && style.equals(lastStyle)) {
                result.set(result.size() - 1, new Inline.TextRun(lastText + text, style));
            } else {
                result.add(inline);
            }
        }
        return result;
    }
}
