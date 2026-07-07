package org.jabref.htmltonode.internal;

import java.util.ArrayList;
import java.util.List;

import org.jabref.htmltonode.model.Block;
import org.jabref.htmltonode.model.Inline;
import org.jabref.htmltonode.model.InlineStyle;

/// Splits TeX math spans out of the parsed model's text runs, turning them into [Inline.Math].
///
/// Recognized delimiters: `$…$` and `\(…\)` (inline style), `$$…$$` and `\[…\]` (display style).
/// Because a `$` is ordinary text outside TeX-sourced content, single-dollar spans follow
/// Pandoc's heuristics: the opening `$` must be followed and the closing `$` preceded by
/// non-whitespace, and the closing `$` must not be followed by a digit — so "costs $5 and $10"
/// stays text. `\$` never opens or closes a span. Code runs (monospace), link text, and `<pre>`
/// blocks are never scanned; a span cannot cross an element boundary (it must lie within one
/// styled run).
public final class MathSegmenter {

    private MathSegmenter() {
    }

    public static List<Block> apply(List<Block> blocks) {
        return blocks.stream().map(MathSegmenter::applyBlock).toList();
    }

    private static Block applyBlock(Block block) {
        return switch (block) {
            case Block.Paragraph(List<Inline> inlines, boolean spaced, List<String> cssClasses) ->
                    new Block.Paragraph(segmentInlines(inlines), spaced, cssClasses);
            case Block.Heading(int level, List<Inline> inlines, List<String> cssClasses) ->
                    new Block.Heading(level, segmentInlines(inlines), cssClasses);
            case Block.ListBlock(boolean ordered, int start, List<Block.ListItem> items) ->
                    new Block.ListBlock(ordered, start,
                            items.stream().map(item -> new Block.ListItem(apply(item.blocks()))).toList());
            case Block.DefinitionList(List<Block.DefinitionItem> items) ->
                    new Block.DefinitionList(items.stream()
                                                  .map(item -> new Block.DefinitionItem(item.term(), apply(item.blocks())))
                                                  .toList());
            case Block.Quote(List<Block> children) -> new Block.Quote(apply(children));
            case Block.Table(List<Block.TableRow> rows) ->
                    new Block.Table(rows.stream()
                                        .map(row -> new Block.TableRow(row.cells().stream()
                                                                          .map(cell -> new Block.TableCell(cell.header(), cell.columnSpan(), apply(cell.blocks())))
                                                                          .toList()))
                                        .toList());
            case Block.Container(List<Block> children, List<String> cssClasses) ->
                    new Block.Container(apply(children), cssClasses);
            // code content: a $ there is code, not math
            case Block.Pre pre -> pre;
            case Block.Rule rule -> rule;
        };
    }

    private static List<Inline> segmentInlines(List<Inline> inlines) {
        List<Inline> result = new ArrayList<>(inlines.size());
        for (Inline inline : inlines) {
            if (inline instanceof Inline.TextRun(String text, InlineStyle style)
                    && !style.monospace()
                    && !style.link()) {
                result.addAll(segmentRun(text, style));
            } else {
                result.add(inline);
            }
        }
        return result;
    }

    private static List<Inline> segmentRun(String text, InlineStyle style) {
        List<Inline> result = new ArrayList<>(1);
        StringBuilder plain = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if ((c == '\\') && (i + 1 < text.length())) {
                char next = text.charAt(i + 1);
                if (next == '$') {
                    // \$ is an escaped literal dollar: never a delimiter, and the backslash is
                    // dropped so the rendered text shows "$" rather than "\$"
                    plain.append('$');
                    i += 2;
                    continue;
                }
                if ((next == '(') || (next == '[')) {
                    String closer = (next == '(') ? "\\)" : "\\]";
                    int end = text.indexOf(closer, i + 2);
                    String tex = (end < 0) ? "" : text.substring(i + 2, end);
                    if (!tex.isBlank()) {
                        emit(result, plain, tex, next == '[', text.substring(i, end + 2), style);
                        i = end + 2;
                        continue;
                    }
                }
            }
            if (c == '$') {
                int end = (i + 1 < text.length()) && (text.charAt(i + 1) == '$')
                        ? closingDoubleDollar(text, i + 2)
                        : closingSingleDollar(text, i + 1);
                if (end >= 0) {
                    boolean display = text.charAt(i + 1) == '$';
                    int texStart = display ? i + 2 : i + 1;
                    int spanEnd = display ? end + 2 : end + 1;
                    emit(result, plain, text.substring(texStart, end), display, text.substring(i, spanEnd), style);
                    i = spanEnd;
                    continue;
                }
            }
            plain.append(c);
            i++;
        }
        if (!plain.isEmpty()) {
            result.add(new Inline.TextRun(plain.toString(), style));
        }
        return result;
    }

    private static void emit(List<Inline> result, StringBuilder plain, String tex, boolean display, String source, InlineStyle style) {
        if (!plain.isEmpty()) {
            result.add(new Inline.TextRun(plain.toString(), style));
            plain.setLength(0);
        }
        result.add(new Inline.Math(tex, display, source, style));
    }

    /// @return the index of the `$$` closing a span whose content starts at `from`, or -1
    private static int closingDoubleDollar(String text, int from) {
        int end = from;
        while ((end = text.indexOf("$$", end)) >= 0) {
            if (!escaped(text, end)) {
                return text.substring(from, end).isBlank() ? -1 : end;
            }
            end++;
        }
        return -1;
    }

    /// @return the index of the `$` closing a span whose content starts at `from`, or -1
    private static int closingSingleDollar(String text, int from) {
        if ((from >= text.length()) || Character.isWhitespace(text.charAt(from)) || (text.charAt(from) == '$')) {
            // opening $ must be followed by non-space content
            return -1;
        }
        for (int end = from + 1; end < text.length(); end++) {
            if ((text.charAt(end) != '$') || escaped(text, end)) {
                continue;
            }
            if (Character.isWhitespace(text.charAt(end - 1))) {
                // closing $ must be preceded by non-space ("x$ and $y" is not a span)
                continue;
            }
            if ((end + 1 < text.length()) && Character.isDigit(text.charAt(end + 1))) {
                // "$5 or $10" — a digit right after the closing $ means currency, not math
                return -1;
            }
            return end;
        }
        return -1;
    }

    private static boolean escaped(String text, int index) {
        return (index > 0) && (text.charAt(index - 1) == '\\');
    }
}
