package org.jabref.htmltonode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import org.jabref.htmltonode.internal.HighlightTextFlow;
import org.jabref.htmltonode.model.Block;
import org.jabref.htmltonode.model.CssLength;
import org.jabref.htmltonode.model.Inline;
import org.jabref.htmltonode.model.InlineStyle;

/// Renders the block model into plain JavaFX nodes: paragraphs become [TextFlow]s in a [VBox],
/// links become styled, clickable [Text] runs (they wrap mid-link like in a browser — no
/// `javafx.controls` dependency), `<mark>`/background runs get real background shapes.
///
/// Style classes for theming: `html-view` (root), `html-paragraph`, `html-pre`, `html-text`,
/// `html-link`, `html-mark`, `html-heading`/`html-h1`…`h6`, `html-list`, `html-list-marker`,
/// `html-blockquote`, `html-table`, `html-hr`, `html-image`, plus all CSS classes carried
/// over from the HTML (`csl-entry`, `error`, …).
public final class FxRenderer {

    private static final double[] HEADING_SCALE = {2.0, 1.5, 1.17, 1.0, 0.83, 0.67};

    private final HtmlRenderOptions options;
    private final double baseSize;
    private final String baseFamily;

    private FxRenderer(HtmlRenderOptions options) {
        this.options = options;
        this.baseSize = options.resolvedBaseFontSize();
        this.baseFamily = options.resolvedBaseFontFamily();
    }

    /// Renders the given blocks. Must be called on the JavaFX application thread when the
    /// result is (or will be) part of a live scene.
    ///
    /// @param blocks  the blocks to render, typically from [HtmlToNode#parse(String, String)]
    /// @param options rendering options
    /// @return a `VBox` with style class `html-view` and the built-in stylesheet attached
    public static Region render(List<Block> blocks, HtmlRenderOptions options) {
        FxRenderer renderer = new FxRenderer(options);
        VBox root = new VBox();
        root.getStyleClass().add("html-view");
        root.getStylesheets().add(HtmlToNode.stylesheet());
        root.setFillWidth(true);
        renderer.appendBlocks(root, blocks);
        return root;
    }

    private void appendBlocks(VBox parent, List<Block> blocks) {
        double previousBottomEm = 0;
        boolean first = true;
        for (Block block : blocks) {
            Node node = renderBlock(block);
            if (node == null) {
                continue;
            }
            double topEm = first ? 0 : Math.max(previousBottomEm, topMarginEm(block));
            double leftPx = leftIndentPx(block);
            if (topEm > 0 || leftPx > 0) {
                VBox.setMargin(node, new Insets(topEm * baseSize, 0, 0, leftPx));
            }
            parent.getChildren().add(node);
            previousBottomEm = bottomMarginEm(block);
            first = false;
        }
    }

    private Node renderBlock(Block block) {
        return switch (block) {
            case Block.Paragraph(List<Inline> inlines, boolean ignoredSpaced, List<String> cssClasses) ->
                    renderFlow(inlines, "html-paragraph", cssClasses, 1.0, 400);
            case Block.Heading(int level, List<Inline> inlines, List<String> cssClasses) -> {
                List<String> classes = new ArrayList<>(cssClasses);
                classes.add("html-heading");
                classes.add("html-h" + level);
                yield renderFlow(inlines, "html-paragraph", classes, HEADING_SCALE[level - 1], 700);
            }
            case Block.Pre(List<Inline> inlines, List<String> cssClasses) ->
                    renderFlow(inlines, "html-pre", cssClasses, 1.0, 400);
            case Block.ListBlock listBlock -> renderList(listBlock);
            case Block.DefinitionList(List<Block.DefinitionItem> items) -> renderDefinitionList(items);
            case Block.Quote(List<Block> children) -> {
                VBox quote = new VBox();
                quote.getStyleClass().add("html-blockquote");
                quote.setFillWidth(true);
                appendBlocks(quote, children);
                yield quote;
            }
            case Block.Rule ignored -> {
                Region rule = new Region();
                rule.getStyleClass().add("html-hr");
                rule.setPrefHeight(1);
                rule.setMinHeight(1);
                rule.setMaxWidth(Double.MAX_VALUE);
                yield rule;
            }
            case Block.Table(List<Block.TableRow> rows) -> renderTable(rows);
            case Block.Container(List<Block> children, List<String> cssClasses) -> {
                VBox container = new VBox();
                container.getStyleClass().addAll(cssClasses);
                container.setFillWidth(true);
                appendBlocks(container, children);
                yield container;
            }
        };
    }

    private Node renderList(Block.ListBlock listBlock) {
        VBox list = new VBox();
        list.getStyleClass().add("html-list");
        list.setFillWidth(true);
        list.setSpacing(0.2 * baseSize);
        int number = listBlock.start();
        for (Block.ListItem item : listBlock.items()) {
            Text marker = new Text(listBlock.ordered() ? number + "." : "•");
            marker.setFont(Font.font(baseFamily, baseSize));
            marker.getStyleClass().addAll("html-text", "html-list-marker");
            HBox markerBox = new HBox(marker);
            markerBox.setAlignment(Pos.TOP_RIGHT);
            markerBox.setMinWidth(1.6 * baseSize);

            VBox content = new VBox();
            content.setFillWidth(true);
            appendBlocks(content, item.blocks());
            HBox.setHgrow(content, Priority.ALWAYS);
            content.setMaxWidth(Double.MAX_VALUE);

            HBox row = new HBox(0.4 * baseSize, markerBox, content);
            row.setFillHeight(true);
            list.getChildren().add(row);
            number++;
        }
        return list;
    }

    private Node renderDefinitionList(List<Block.DefinitionItem> items) {
        VBox list = new VBox();
        list.getStyleClass().add("html-definition-list");
        list.setFillWidth(true);
        for (Block.DefinitionItem item : items) {
            VBox itemBox = new VBox();
            itemBox.setFillWidth(true);
            appendBlocks(itemBox, item.blocks());
            if (!item.term()) {
                VBox.setMargin(itemBox, new Insets(0, 0, 0, 2.0 * baseSize));
            }
            list.getChildren().add(itemBox);
        }
        return list;
    }

    private Node renderTable(List<Block.TableRow> rows) {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("html-table");
        grid.setHgap(0.6 * baseSize);
        grid.setVgap(0.2 * baseSize);
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            int columnIndex = 0;
            for (Block.TableCell cell : rows.get(rowIndex).cells()) {
                VBox cellBox = new VBox();
                cellBox.setFillWidth(true);
                appendBlocks(cellBox, cell.blocks());
                GridPane.setConstraints(cellBox, columnIndex, rowIndex);
                if (cell.columnSpan() > 1) {
                    GridPane.setColumnSpan(cellBox, cell.columnSpan());
                }
                grid.getChildren().add(cellBox);
                columnIndex += cell.columnSpan();
            }
        }
        return grid;
    }

    /// Renders inline content into a [HighlightTextFlow]. `scale`/`minWeight` implement
    /// heading sizing without touching the parsed model.
    private TextFlow renderFlow(List<Inline> inlines, String baseClass, List<String> cssClasses, double scale, int minWeight) {
        HighlightTextFlow flow = new HighlightTextFlow();
        flow.getStyleClass().add(baseClass);
        flow.getStyleClass().addAll(cssClasses);
        int charIndex = 0;
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
                    int runStart = charIndex;
                    for (TextPiece piece : splitForSmallCaps(text, effective)) {
                        Text textNode = createText(piece.text(), effective, piece.sizeFactor());
                        flow.getChildren().add(textNode);
                        charIndex += piece.text().length();
                    }
                    if (effective.background() != null) {
                        Optional<Color> background = parseColor(effective.background());
                        if (background.isPresent()) {
                            flow.addHighlight(runStart, charIndex, background.get());
                        }
                    }
                }
                case Inline.LineBreak ignored -> {
                    Text newline = new Text("\n");
                    newline.getStyleClass().add("html-text");
                    flow.getChildren().add(newline);
                    charIndex += 1;
                }
                case Inline.Image image -> {
                    Node imageNode = renderImage(image);
                    if (imageNode != null) {
                        flow.getChildren().add(imageNode);
                        charIndex += 1;
                    }
                }
            }
        }
        return flow;
    }

    private Text createText(String content, InlineStyle style, double sizeFactor) {
        Text text = new Text(content);
        text.getStyleClass().add("html-text");
        double size = baseSize * style.fontScale() * sizeFactor;
        String family = style.fontFamily() != null
                ? style.fontFamily()
                : (style.monospace() ? options.monospaceFontFamily() : baseFamily);
        text.setFont(Font.font(family,
                FontWeight.findByWeight(style.fontWeight()),
                style.italic() ? FontPosture.ITALIC : FontPosture.REGULAR,
                size));
        text.setUnderline(style.underline() || style.link());
        text.setStrikethrough(style.strikethrough());

        switch (style.verticalPosition()) {
            case SUPER -> text.setTranslateY(-0.42 * size);
            case SUB -> text.setTranslateY(0.18 * size);
            case NORMAL -> {
            }
        }

        if (style.link()) {
            text.getStyleClass().add("html-link");
            text.setCursor(Cursor.HAND);
            text.setFill(Color.web("#0b66c3"));
            String href = style.href();
            text.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY) {
                    options.linkHandler().accept(href);
                }
            });
        }

        // Pin the font via an inline style as well: JavaFX author stylesheets and inline styles
        // inherited from ancestors (e.g. an application setting "-fx-font-size" on the scene
        // root) override Font values set from code on every CSS pass - which would lose bold/
        // italic or resize the text whenever the node is reparented or focus changes.
        StringBuilder inlineStyle = new StringBuilder()
                .append("-fx-font-family: \"").append(family).append("\"; ")
                .append("-fx-font-size: ").append(String.format(Locale.ROOT, "%.3f", size)).append("px; ")
                .append("-fx-font-weight: ").append(style.fontWeight()).append("; ")
                .append("-fx-font-style: ").append(style.italic() ? "italic" : "normal").append(";");

        // explicit HTML colors win over stylesheets: set both the property and an inline style
        if (style.color() != null) {
            parseColor(style.color()).ifPresent(color -> {
                text.setFill(color);
                inlineStyle.append(" -fx-fill: ").append(toCssColor(color)).append(";");
            });
        }
        text.setStyle(inlineStyle.toString());
        return text;
    }

    private Node renderImage(Inline.Image image) {
        if (!options.renderImages() || !allowedImageSource(image.source())) {
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

    private boolean allowedImageSource(String source) {
        String lower = source.toLowerCase(Locale.ROOT);
        if (lower.startsWith("file:") || lower.startsWith("data:") || lower.startsWith("jar:")) {
            return true;
        }
        return options.loadRemoteImages() && (lower.startsWith("http:") || lower.startsWith("https:"));
    }

    private record TextPiece(String text, double sizeFactor) {
    }

    /// Small-caps emulation: lowercase stretches are rendered as smaller capitals.
    private static List<TextPiece> splitForSmallCaps(String text, InlineStyle style) {
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

    private static Optional<Color> parseColor(String value) {
        try {
            return Optional.of(Color.web(value.trim()));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    private static String toCssColor(Color color) {
        return String.format(Locale.ROOT, "rgba(%d,%d,%d,%.3f)",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255),
                color.getOpacity());
    }

    private static double topMarginEm(Block block) {
        return switch (block) {
            case Block.Paragraph(List<Inline> ignored, boolean spaced, List<String> ignoredClasses) -> spaced ? 1.0 : 0;
            case Block.Heading ignored -> 1.0;
            case Block.ListBlock ignored -> 1.0;
            case Block.DefinitionList ignored -> 1.0;
            case Block.Quote ignored -> 1.0;
            case Block.Pre ignored -> 1.0;
            case Block.Table ignored -> 1.0;
            case Block.Rule ignored -> 0.5;
            case Block.Container ignored -> 0;
        };
    }

    private static double bottomMarginEm(Block block) {
        return switch (block) {
            case Block.Heading ignored -> 0.4;
            default -> topMarginEm(block);
        };
    }

    private double leftIndentPx(Block block) {
        return block instanceof Block.Quote ? 2.0 * baseSize : 0;
    }
}
