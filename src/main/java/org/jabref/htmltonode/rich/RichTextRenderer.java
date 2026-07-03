package org.jabref.htmltonode.rich;

import java.util.List;

import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

import org.jabref.htmltonode.FxRenderer;
import org.jabref.htmltonode.HtmlRenderOptions;
import org.jabref.htmltonode.internal.RenderSupport;
import org.jabref.htmltonode.model.Block;
import org.jabref.htmltonode.model.Inline;
import org.jabref.htmltonode.model.InlineStyle;

import jfx.incubator.scene.control.richtext.RichTextArea;
import jfx.incubator.scene.control.richtext.TextPos;
import jfx.incubator.scene.control.richtext.model.SimpleViewOnlyStyledModel;
import jfx.incubator.scene.control.richtext.model.StyleAttribute;
import jfx.incubator.scene.control.richtext.model.StyleAttributeMap;
import jfx.incubator.scene.control.richtext.model.StyledTextModel;
import org.jspecify.annotations.Nullable;

/// Renders the block model into a [RichTextArea] (JavaFX incubator control) — the
/// selection-, caret- and accessibility-capable alternative to [FxRenderer].
///
/// Inline content maps to styled segments ([StyleAttributeMap]); `<mark>`/background runs map
/// to native highlights; links carry the custom [#HREF] attribute and are resolved on click via
/// [RichTextArea#getTextPosition]. Tables and horizontal rules are embedded as node paragraphs
/// rendered by [FxRenderer]. Known interim gaps (tracked upstream, see the project's issue #2):
/// sub-/superscript lose their baseline shift, numeric font weights collapse to bold/normal,
/// and inline images inside a paragraph are skipped.
public final class RichTextRenderer {

    /// Character attribute carrying a link target; resolved on mouse click.
    public static final StyleAttribute<String> HREF = new StyleAttribute<>("HREF", String.class, false);

    /// Fallback link color; matches FxRenderer's un-themed link fill.
    private static final Color LINK_COLOR = Color.web("#0b66c3");

    private final HtmlRenderOptions options;
    private final double baseSize;
    private final String baseFamily;
    private final SimpleViewOnlyStyledModel model = new SimpleViewOnlyStyledModel();

    /// characters already added to the paragraph currently being built
    private int paragraphOffset;
    /// whether any paragraph content or attributes were emitted since the last `nl()`
    private boolean paragraphDirty;
    /// left indent (in px) applied to paragraphs, e.g. inside `<blockquote>`
    private double indent;
    /// marker text (e.g. `• `, `3. `) to prepend to the next paragraph
    private @Nullable String pendingMarker;

    private RichTextRenderer(HtmlRenderOptions options) {
        this.options = options;
        this.baseSize = options.resolvedBaseFontSize();
        this.baseFamily = options.resolvedBaseFontFamily();
    }

    /// Builds the styled document model. Does not require the JavaFX toolkit.
    ///
    /// @param blocks  the blocks to render, typically from `HtmlToNode.parse(...)`
    /// @param options rendering options
    /// @return a read-only model for [RichTextArea#setModel]
    public static StyledTextModel buildModel(List<Block> blocks, HtmlRenderOptions options) {
        RichTextRenderer renderer = new RichTextRenderer(options);
        renderer.appendBlocks(blocks);
        return renderer.model;
    }

    /// Renders the blocks into a ready-to-use, read-only [RichTextArea] with wrapping and
    /// link-click handling. Must be used on the JavaFX application thread.
    ///
    /// @param blocks  the blocks to render
    /// @param options rendering options
    /// @return the configured control
    public static RichTextArea render(List<Block> blocks, HtmlRenderOptions options) {
        RichTextArea area = new RichTextArea(buildModel(blocks, options));
        configure(area, options);
        return area;
    }

    /// Applies the standard preview configuration (read-only, wrapping, link clicks) to `area`.
    public static void configure(RichTextArea area, HtmlRenderOptions options) {
        area.setEditable(false);
        area.setWrapText(true);
        area.getStyleClass().add("html-rich-view");
        area.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.isStillSincePress()) {
                TextPos position = area.getTextPosition(event.getScreenX(), event.getScreenY());
                if (position != null) {
                    String href = area.getModel().getStyleAttributeMap(null, position).get(HREF);
                    if (href != null) {
                        options.linkHandler().accept(href);
                    }
                }
            }
        });
    }

    private void appendBlocks(List<Block> blocks) {
        for (Block block : blocks) {
            switch (block) {
                case Block.Paragraph(List<Inline> inlines, boolean spaced, List<String> ignored) ->
                        paragraph(inlines, 1.0, 400, spaced ? baseSize : 0);
                case Block.Heading(int level, List<Inline> inlines, List<String> ignored) ->
                        paragraph(inlines, RenderSupport.headingScale(level), 700, baseSize);
                case Block.Pre(List<Inline> inlines, List<String> ignored) ->
                        paragraph(inlines, 1.0, 400, baseSize);
                case Block.Container(List<Block> children, List<String> ignored) ->
                        appendBlocks(children);
                case Block.Quote(List<Block> children) -> {
                    indent += 2 * baseSize;
                    appendBlocks(children);
                    indent -= 2 * baseSize;
                }
                case Block.ListBlock(boolean ordered, int start, List<Block.ListItem> items) -> {
                    int number = start;
                    for (Block.ListItem item : items) {
                        pendingMarker = ordered ? (number++) + ". " : "• ";
                        indent += 1.6 * baseSize;
                        appendBlocks(item.blocks());
                        indent -= 1.6 * baseSize;
                        pendingMarker = null;
                    }
                }
                case Block.DefinitionList(List<Block.DefinitionItem> items) -> {
                    for (Block.DefinitionItem item : items) {
                        if (item.term()) {
                            appendBlocks(item.blocks());
                        } else {
                            indent += 2 * baseSize;
                            appendBlocks(item.blocks());
                            indent -= 2 * baseSize;
                        }
                    }
                }
                case Block.Rule ignored ->
                        embed(List.of(block));
                case Block.Table ignored ->
                        embed(List.of(block));
            }
        }
    }

    /// Embeds blocks as a node paragraph rendered by [FxRenderer] (tables, rules).
    private void embed(List<Block> blocks) {
        closeParagraph();
        List<Block> copy = List.copyOf(blocks);
        model.addParagraph(() -> (Region) FxRenderer.render(copy, options));
        paragraphDirty = true;
        closeParagraph();
    }

    private void paragraph(List<Inline> inlines, double scale, int minWeight, double spaceAbove) {
        closeParagraph();
        beginParagraph(spaceAbove);
        for (Inline inline : inlines) {
            switch (inline) {
                case Inline.TextRun(String text, InlineStyle style) -> {
                    InlineStyle effective = style;
                    if (scale != 1.0) {
                        effective = effective.withFontScale(effective.fontScale() * scale);
                    }
                    if (effective.fontWeight() < minWeight) {
                        effective = effective.withFontWeight(minWeight);
                    }
                    appendRun(text, effective, spaceAbove);
                }
                case Inline.LineBreak ignored -> {
                    closeParagraph();
                    beginParagraph(0);
                }
                case Inline.Image ignored -> {
                    // Inline images are not supported by the rich renderer yet (Phase 2 / upstream)
                }
            }
        }
        closeParagraph();
    }

    private void appendRun(String text, InlineStyle style, double spaceAbove) {
        // Hard line breaks inside a run (from `pre`) become separate paragraphs
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                closeParagraph();
                beginParagraph(0);
            }
            String line = lines[i];
            if (line.isEmpty()) {
                paragraphDirty = true;
                continue;
            }
            int runStart = paragraphOffset;
            for (RenderSupport.TextPiece piece : RenderSupport.splitForSmallCaps(line, style)) {
                model.addSegment(piece.text(), attributes(style, piece.sizeFactor()));
                paragraphOffset += piece.text().length();
                paragraphDirty = true;
            }
            if (style.background() != null) {
                RenderSupport.parseColor(style.background())
                             .ifPresent(color -> model.highlight(runStart, paragraphOffset - runStart, color));
            }
        }
    }

    private void beginParagraph(double spaceAbove) {
        StyleAttributeMap.Builder paragraphAttributes = StyleAttributeMap.builder();
        boolean hasAttributes = false;
        if (spaceAbove > 0) {
            paragraphAttributes.setSpaceAbove(spaceAbove);
            hasAttributes = true;
        }
        if (indent > 0) {
            paragraphAttributes.setSpaceLeft(indent);
            hasAttributes = true;
        }
        if (hasAttributes) {
            model.setParagraphAttributes(paragraphAttributes.build());
            paragraphDirty = true;
        }
        if (pendingMarker != null) {
            model.addSegment(pendingMarker, attributes(InlineStyle.DEFAULT, 1.0));
            paragraphOffset += pendingMarker.length();
            paragraphDirty = true;
            pendingMarker = null;
        }
    }

    private void closeParagraph() {
        if (paragraphDirty) {
            model.nl();
            paragraphDirty = false;
            paragraphOffset = 0;
        }
    }

    private StyleAttributeMap attributes(InlineStyle style, double sizeFactor) {
        StyleAttributeMap.Builder builder = StyleAttributeMap.builder();
        builder.setFontSize(baseSize * style.fontScale() * sizeFactor);
        String family = style.fontFamily() != null
                ? style.fontFamily()
                : (style.monospace() ? options.monospaceFontFamily() : baseFamily);
        builder.setFontFamily(family);
        if (style.bold()) {
            builder.setBold(true);
        }
        if (style.italic()) {
            builder.setItalic(true);
        }
        if (style.underline() || style.link()) {
            builder.setUnderline(true);
        }
        if (style.strikethrough()) {
            builder.setStrikeThrough(true);
        }
        if (style.color() != null) {
            RenderSupport.parseColor(style.color()).ifPresent(builder::setTextColor);
        } else if (style.link()) {
            builder.setTextColor(LINK_COLOR);
        }
        if (style.link()) {
            builder.set(HREF, style.href());
        }
        return builder.build();
    }
}
