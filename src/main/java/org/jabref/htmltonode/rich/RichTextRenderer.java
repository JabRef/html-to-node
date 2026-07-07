package org.jabref.htmltonode.rich;

import java.util.List;

import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

import org.jabref.htmltonode.FxRenderer;
import org.jabref.htmltonode.HtmlRenderOptions;
import org.jabref.htmltonode.internal.MathRendering;
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
/// rendered by [FxRenderer]; images are embedded as inline nodes. Known interim gaps (tracked
/// upstream, see the project's issue #2): sub-/superscript lose their baseline shift and
/// numeric font weights collapse to bold/normal.
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
    /// whether the model's last paragraph is occupied (text, node, or a preserved blank line)
    private boolean paragraphOccupied;
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
        RichTextArea area = new HtmlRichTextArea(buildModel(blocks, options));
        configure(area, options);
        return area;
    }

    /// Applies the standard preview configuration (read-only, wrapping, link clicks) to `area`.
    public static void configure(RichTextArea area, HtmlRenderOptions options) {
        area.setEditable(false);
        area.setWrapText(true);
        area.getStyleClass().add("html-rich-view");
        area.setOnMouseClicked(event -> {
            // getTextPosition(x, y) snaps *any* coordinate to the nearest caret position, so a click in
            // the empty area past a trailing link would otherwise resolve to — and open — that link.
            // Requiring the pick to land on a Text glyph (every run is a Text node) keeps clicks on the
            // surrounding whitespace, embedded images, and other non-text nodes inert.
            if (event.getButton() == MouseButton.PRIMARY && event.isStillSincePress()
                    && event.getPickResult().getIntersectedNode() instanceof Text) {
                TextPos position = area.getTextPosition(event.getScreenX(), event.getScreenY());
                if (position != null) {
                    // Node segments (embedded images, tables) carry no character attributes, so the
                    // model returns null there — only text runs can hold an HREF.
                    StyleAttributeMap attributes = area.getModel().getStyleAttributeMap(null, position);
                    String href = attributes != null ? attributes.get(HREF) : null;
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

    /// Embeds blocks as a self-contained node paragraph rendered by [FxRenderer] (tables, rules).
    private void embed(List<Block> blocks) {
        separateParagraph();
        List<Block> copy = List.copyOf(blocks);
        model.addParagraph(() -> (Region) FxRenderer.render(copy, options));
        paragraphOccupied = true;
    }

    private void paragraph(List<Inline> inlines, double scale, int minWeight, double spaceAbove) {
        startParagraph(spaceAbove);
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
                    appendRun(text, effective);
                }
                case Inline.LineBreak ignored -> {
                    // A break renders as a blank line even with no content of its own, so a second
                    // consecutive break (e.g. `<br><br>`) still triggers a fresh model.nl() in the next
                    // startParagraph() call instead of being swallowed by separateParagraph()'s
                    // occupied-check (mirrors the empty-line handling for preserved "\n\n" in appendRun).
                    startParagraph(0);
                    paragraphOccupied = true;
                }
                case Inline.Image image -> {
                    if (RenderSupport.createImageView(image, options, baseSize) != null) {
                        model.addNodeSegment(() -> RenderSupport.createImageView(image, options, baseSize));
                        paragraphOccupied = true;
                    }
                }
                case Inline.Math math -> {
                    if (MathRendering.isRenderable(math)) {
                        double fontSize = baseSize * math.style().fontScale() * scale;
                        // the node factory runs on the FX thread when the paragraph is shown;
                        // if the image subsystem is unavailable there, show the TeX source
                        model.addNodeSegment(() -> {
                            Node node = MathRendering.createMathNode(math, fontSize);
                            return node != null ? node : new Text(math.source());
                        });
                        paragraphOccupied = true;
                    } else {
                        appendRun(math.source(), math.style());
                    }
                }
            }
        }
    }

    private void appendRun(String text, InlineStyle style) {
        // Hard line breaks inside a run (from `pre`) become separate paragraphs
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                startParagraph(0);
            }
            String line = lines[i];
            if (line.isEmpty()) {
                paragraphOccupied = true;
                continue;
            }
            int runStart = paragraphOffset;
            for (RenderSupport.TextPiece piece : RenderSupport.splitForSmallCaps(line, style)) {
                model.addSegment(piece.text(), attributes(style, piece.sizeFactor()));
                paragraphOffset += piece.text().length();
                paragraphOccupied = true;
            }
            if (style.background() != null) {
                RenderSupport.parseColor(style.background())
                             .ifPresent(color -> model.highlight(runStart, paragraphOffset - runStart, color));
            }
        }
    }

    /// Ends the previous paragraph (if occupied) and applies attributes/markers to the new one.
    private void startParagraph(double spaceAbove) {
        separateParagraph();
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
            paragraphOccupied = true;
        }
        if (pendingMarker != null) {
            model.addSegment(pendingMarker, attributes(InlineStyle.DEFAULT, 1.0));
            paragraphOffset += pendingMarker.length();
            paragraphOccupied = true;
            pendingMarker = null;
        }
    }

    private void separateParagraph() {
        if (paragraphOccupied) {
            model.nl();
            paragraphOccupied = false;
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
