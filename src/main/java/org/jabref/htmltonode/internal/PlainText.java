package org.jabref.htmltonode.internal;

import java.util.List;

import org.jabref.htmltonode.model.Block;
import org.jabref.htmltonode.model.Inline;

/// Extracts readable plain text from the block model, roughly like a browser's `innerText`
/// (JabRef uses that for "Copy citation (text)" today).
public final class PlainText {

    private PlainText() {
    }

    public static String of(List<Block> blocks) {
        StringBuilder builder = new StringBuilder();
        appendBlocks(builder, blocks);
        return builder.toString()
                      .replaceAll("\\n{3,}", "\n\n")
                      .strip();
    }

    private static void appendBlocks(StringBuilder builder, List<Block> blocks) {
        for (Block block : blocks) {
            switch (block) {
                case Block.Paragraph(List<Inline> inlines, boolean spaced, List<String> ignored) -> {
                    if (spaced) {
                        ensureBlankLine(builder);
                    }
                    appendInlines(builder, inlines);
                    newLine(builder);
                    if (spaced) {
                        ensureBlankLine(builder);
                    }
                }
                case Block.Heading(int ignoredLevel, List<Inline> inlines, List<String> ignored) -> {
                    ensureBlankLine(builder);
                    appendInlines(builder, inlines);
                    newLine(builder);
                    ensureBlankLine(builder);
                }
                case Block.ListBlock(boolean ordered, int start, List<Block.ListItem> items) -> {
                    for (int i = 0; i < items.size(); i++) {
                        builder.append(ordered ? (start + i) + ". " : "• ");
                        appendBlocks(builder, items.get(i).blocks());
                        newLine(builder);
                    }
                }
                case Block.DefinitionList(List<Block.DefinitionItem> items) -> {
                    for (Block.DefinitionItem item : items) {
                        appendBlocks(builder, item.blocks());
                        newLine(builder);
                    }
                }
                case Block.Quote(List<Block> children) -> appendBlocks(builder, children);
                case Block.Pre(List<Inline> inlines, List<String> ignored) -> {
                    appendInlines(builder, inlines);
                    newLine(builder);
                }
                case Block.Rule ignored -> newLine(builder);
                case Block.Table(List<Block.TableRow> rows) -> {
                    for (Block.TableRow row : rows) {
                        for (int i = 0; i < row.cells().size(); i++) {
                            if (i > 0) {
                                builder.append('\t');
                            }
                            appendBlocksInline(builder, row.cells().get(i).blocks());
                        }
                        newLine(builder);
                    }
                }
                case Block.Container(List<Block> children, List<String> ignored) -> appendBlocks(builder, children);
            }
        }
    }

    /// Table cells: block boundaries become single spaces, not newlines.
    private static void appendBlocksInline(StringBuilder builder, List<Block> blocks) {
        StringBuilder cell = new StringBuilder();
        appendBlocks(cell, blocks);
        builder.append(cell.toString().strip().replaceAll("\\s*\\n\\s*", " "));
    }

    private static void appendInlines(StringBuilder builder, List<Inline> inlines) {
        for (Inline inline : inlines) {
            switch (inline) {
                case Inline.TextRun(String text, org.jabref.htmltonode.model.InlineStyle ignored) -> builder.append(text);
                case Inline.LineBreak ignored -> newLine(builder);
                case Inline.Image(String ignoredSource, org.jabref.htmltonode.model.CssLength ignoredW,
                                  org.jabref.htmltonode.model.CssLength ignoredH, boolean ignoredBlock, String alt) -> {
                    if (alt != null && !alt.isBlank()) {
                        builder.append(alt);
                    }
                }
                // the original TeX including delimiters — plain-text copy round-trips
                case Inline.Math math -> builder.append(math.source());
            }
        }
    }

    private static void newLine(StringBuilder builder) {
        if (!builder.isEmpty() && builder.charAt(builder.length() - 1) != '\n') {
            builder.append('\n');
        }
    }

    private static void ensureBlankLine(StringBuilder builder) {
        if (builder.isEmpty()) {
            return;
        }
        newLine(builder);
        builder.append('\n');
    }
}
