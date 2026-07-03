package org.jabref.htmltonode.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jabref.htmltonode.model.Block;
import org.jabref.htmltonode.model.CssLength;
import org.jabref.htmltonode.model.Inline;
import org.jabref.htmltonode.model.InlineStyle;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

/// Parses HTML into the block/inline model. Uses jsoup, so malformed markup, entities and
/// `<base href>` behave like in a browser. Unknown tags degrade gracefully: unknown inline
/// tags are transparent, unknown block tags act like `<div>`.
public final class HtmlToModel {

    /// Legacy `<font size="1..7">` factors relative to the base size (size 3 = 1.0)
    private static final double[] FONT_SIZE_FACTORS = {0.625, 0.8125, 1.0, 1.125, 1.5, 2.0, 3.0};

    private HtmlToModel() {
    }

    public static List<Block> parse(String html, String baseUri) {
        Document document = Jsoup.parse(html == null ? "" : html, baseUri == null ? "" : baseUri);
        BlockCollector root = new BlockCollector(false);
        walkChildren(document.body(), InlineStyle.DEFAULT, WhiteSpaceMode.NORMAL, root);
        return root.finish();
    }

    private static void walkChildren(Element element, InlineStyle style, WhiteSpaceMode whiteSpace, BlockCollector out) {
        for (Node child : element.childNodes()) {
            if (child instanceof TextNode textNode) {
                out.appendText(textNode.getWholeText(), style, whiteSpace);
            } else if (child instanceof Element childElement) {
                walkElement(childElement, style, whiteSpace, out);
            }
            // comments, doctype, data nodes: ignored
        }
    }

    private static void walkElement(Element element, InlineStyle inherited, WhiteSpaceMode whiteSpace, BlockCollector out) {
        String tag = element.normalName();
        switch (tag) {
            case "script", "style", "head", "template", "title", "meta", "link", "base", "iframe", "object", "svg", "noscript" -> {
                // no visible content
            }
            case "br" -> out.appendLineBreak();
            case "img" -> appendImage(element, out);
            case "hr" -> out.add(new Block.Rule());
            case "p" -> addParagraphLike(element, inherited, whiteSpace, out, true);
            case "h1", "h2", "h3", "h4", "h5", "h6" -> addHeading(element, tag.charAt(1) - '0', inherited, whiteSpace, out);
            case "ul", "ol" -> addList(element, "ol".equals(tag), inherited, whiteSpace, out);
            case "dl" -> addDefinitionList(element, inherited, whiteSpace, out);
            case "blockquote" -> out.add(new Block.Quote(parseBlocks(element, deriveStyle(element, inherited), childWhiteSpace(element, whiteSpace))));
            case "pre" -> addPre(element, inherited, out);
            case "table" -> addTable(element, inherited, whiteSpace, out);
            default -> {
                InlineStyle derived = deriveStyle(element, inherited);
                WhiteSpaceMode childWhiteSpace = childWhiteSpace(element, whiteSpace);
                if (element.tag().isBlock()) {
                    // div and every other block-ish element (stray li/td as well)
                    addParagraphLike(element, inherited, whiteSpace, out, false);
                } else {
                    // inline element (known or unknown): style is derived, content flows in place
                    walkChildren(element, derived, childWhiteSpace, out);
                }
            }
        }
    }

    /// `<p>` and `<div>`-like elements: a single all-inline child list collapses into one
    /// [Block.Paragraph]; mixed content becomes a [Block.Container].
    private static void addParagraphLike(Element element, InlineStyle inherited, WhiteSpaceMode whiteSpace, BlockCollector out, boolean spaced) {
        InlineStyle derived = deriveStyle(element, inherited);
        WhiteSpaceMode childWhiteSpace = childWhiteSpace(element, whiteSpace);
        List<String> cssClasses = List.copyOf(element.classNames());

        BlockCollector sub = new BlockCollector(childWhiteSpace == WhiteSpaceMode.PRESERVE);
        walkChildren(element, derived, childWhiteSpace, sub);
        List<Block> blocks = sub.finish();

        if (blocks.isEmpty()) {
            return;
        }
        if (blocks.size() == 1
                && blocks.getFirst() instanceof Block.Paragraph(List<Inline> inlines, boolean innerSpaced, List<String> innerClasses)
                && !innerSpaced
                && innerClasses.isEmpty()) {
            out.add(new Block.Paragraph(inlines, spaced, cssClasses));
            return;
        }
        if (cssClasses.isEmpty() && !spaced) {
            // transparent grouping div
            blocks.forEach(out::add);
            return;
        }
        out.add(new Block.Container(blocks, cssClasses));
    }

    private static void addHeading(Element element, int level, InlineStyle inherited, WhiteSpaceMode whiteSpace, BlockCollector out) {
        InlineStyle derived = deriveStyle(element, inherited);
        BlockCollector sub = new BlockCollector(false);
        walkChildren(element, derived, childWhiteSpace(element, whiteSpace), sub);
        List<Inline> inlines = flattenToInlines(sub.finish());
        if (!inlines.isEmpty()) {
            out.add(new Block.Heading(level, inlines, List.copyOf(element.classNames())));
        }
    }

    private static void addList(Element element, boolean ordered, InlineStyle inherited, WhiteSpaceMode whiteSpace, BlockCollector out) {
        InlineStyle derived = deriveStyle(element, inherited);
        WhiteSpaceMode childWhiteSpace = childWhiteSpace(element, whiteSpace);
        List<Block.ListItem> items = new ArrayList<>();
        for (Element child : element.children()) {
            if ("li".equals(child.normalName())) {
                items.add(new Block.ListItem(parseBlocks(child, deriveStyle(child, derived), childWhiteSpace(child, childWhiteSpace))));
            }
        }
        int start = 1;
        if (ordered && element.hasAttr("start")) {
            try {
                start = Integer.parseInt(element.attr("start").trim());
            } catch (NumberFormatException e) {
                // keep 1
            }
        }
        if (!items.isEmpty()) {
            out.add(new Block.ListBlock(ordered, start, items));
        }
    }

    private static void addDefinitionList(Element element, InlineStyle inherited, WhiteSpaceMode whiteSpace, BlockCollector out) {
        InlineStyle derived = deriveStyle(element, inherited);
        List<Block.DefinitionItem> items = new ArrayList<>();
        for (Element child : element.children()) {
            String childTag = child.normalName();
            if ("dt".equals(childTag) || "dd".equals(childTag)) {
                items.add(new Block.DefinitionItem("dt".equals(childTag),
                        parseBlocks(child, deriveStyle(child, derived), childWhiteSpace(child, whiteSpace))));
            }
        }
        if (!items.isEmpty()) {
            out.add(new Block.DefinitionList(items));
        }
    }

    private static void addPre(Element element, InlineStyle inherited, BlockCollector out) {
        InlineStyle derived = deriveStyle(element, inherited).withMonospace(true);
        BlockCollector sub = new BlockCollector(true);
        walkChildren(element, derived, WhiteSpaceMode.PRESERVE, sub);
        List<Inline> inlines = trimPreEdges(flattenToInlines(sub.finish()));
        if (!inlines.isEmpty()) {
            out.add(new Block.Pre(inlines, List.copyOf(element.classNames())));
        }
    }

    private static void addTable(Element element, InlineStyle inherited, WhiteSpaceMode whiteSpace, BlockCollector out) {
        InlineStyle derived = deriveStyle(element, inherited);
        List<Block.TableRow> rows = new ArrayList<>();
        for (Element section : element.children()) {
            switch (section.normalName()) {
                case "tr" -> addTableRow(section, derived, whiteSpace, rows);
                case "thead", "tbody", "tfoot" -> {
                    for (Element row : section.children()) {
                        if ("tr".equals(row.normalName())) {
                            addTableRow(row, derived, whiteSpace, rows);
                        }
                    }
                }
                default -> {
                    // caption/colgroup: not rendered
                }
            }
        }
        if (!rows.isEmpty()) {
            out.add(new Block.Table(rows));
        }
    }

    private static void addTableRow(Element row, InlineStyle inherited, WhiteSpaceMode whiteSpace, List<Block.TableRow> rows) {
        List<Block.TableCell> cells = new ArrayList<>();
        for (Element cell : row.children()) {
            String tag = cell.normalName();
            boolean header = "th".equals(tag);
            if (header || "td".equals(tag)) {
                InlineStyle cellStyle = deriveStyle(cell, header ? inherited.withFontWeight(700) : inherited);
                int columnSpan = 1;
                try {
                    columnSpan = Math.max(1, Integer.parseInt(cell.attr("colspan").trim()));
                } catch (NumberFormatException e) {
                    // keep 1
                }
                cells.add(new Block.TableCell(header, columnSpan, parseBlocks(cell, cellStyle, childWhiteSpace(cell, whiteSpace))));
            }
        }
        if (!cells.isEmpty()) {
            rows.add(new Block.TableRow(cells));
        }
    }

    private static List<Block> parseBlocks(Element element, InlineStyle style, WhiteSpaceMode whiteSpace) {
        BlockCollector sub = new BlockCollector(whiteSpace == WhiteSpaceMode.PRESERVE);
        walkChildren(element, style, whiteSpace, sub);
        return sub.finish();
    }

    private static void appendImage(Element element, BlockCollector out) {
        String source = element.absUrl("src");
        if (source.isEmpty()) {
            source = element.attr("src");
        }
        if (source.isEmpty()) {
            return;
        }
        String styleAttribute = element.attr("style");
        CssLength width = CssStyleParser.length(styleAttribute, "width")
                .orElseGet(() -> attributeLength(element, "width"));
        CssLength height = CssStyleParser.length(styleAttribute, "height")
                .orElseGet(() -> attributeLength(element, "height"));
        String alt = element.attr("alt");
        out.appendImage(new Inline.Image(source, width, height, CssStyleParser.isDisplayBlock(styleAttribute), alt.isEmpty() ? null : alt));
    }

    private static CssLength attributeLength(Element element, String attribute) {
        if (!element.hasAttr(attribute)) {
            return null;
        }
        return CssStyleParser.parseLength(element.attr(attribute)).orElse(null);
    }

    /// Tag semantics plus the element's `style` attribute, resolved onto `inherited`.
    private static InlineStyle deriveStyle(Element element, InlineStyle inherited) {
        InlineStyle style = inherited;
        switch (element.normalName()) {
            case "b", "strong" -> style = style.withFontWeight(700);
            case "i", "em", "cite", "dfn", "var" -> style = style.withItalic(true);
            case "u", "ins" -> style = style.withUnderline(true);
            case "s", "strike", "del" -> style = style.withStrikethrough(true);
            case "sub" -> style = style.withVerticalPosition(InlineStyle.VerticalPosition.SUB).withFontScale(style.fontScale() * 0.8);
            case "sup" -> style = style.withVerticalPosition(InlineStyle.VerticalPosition.SUPER).withFontScale(style.fontScale() * 0.8);
            case "small" -> style = style.withFontScale(style.fontScale() / 1.2);
            case "big" -> style = style.withFontScale(style.fontScale() * 1.2);
            case "code", "tt", "kbd", "samp" -> style = style.withMonospace(true);
            case "mark" -> style = style.withBackground("yellow");
            case "font" -> style = applyFontElement(element, style);
            case "a" -> {
                String href = element.absUrl("href");
                if (href.isEmpty()) {
                    href = element.attr("href").trim();
                }
                if (!href.isEmpty()) {
                    style = style.withHref(href);
                }
            }
            default -> {
                // span and friends: only the style attribute matters
            }
        }
        String styleAttribute = element.attr("style");
        if (!styleAttribute.isEmpty()) {
            style = CssStyleParser.apply(style, styleAttribute);
        }
        return style;
    }

    private static InlineStyle applyFontElement(Element element, InlineStyle style) {
        InlineStyle result = style;
        String face = element.attr("face").trim();
        if (!face.isEmpty()) {
            String first = face.split(",")[0].trim().replace("\"", "").replace("'", "");
            result = switch (first.toLowerCase(Locale.ROOT)) {
                case "sans-serif", "" -> result;
                case "serif" -> result.withFontFamily("Serif");
                case "monospace" -> result.withMonospace(true);
                default -> result.withFontFamily(first);
            };
        }
        String color = element.attr("color").trim();
        if (!color.isEmpty()) {
            result = result.withColor(color);
        }
        String size = element.attr("size").trim();
        if (!size.isEmpty()) {
            try {
                int index;
                if (size.startsWith("+") || size.startsWith("-")) {
                    index = 3 + Integer.parseInt(size);
                } else {
                    index = Integer.parseInt(size);
                }
                result = result.withFontScale(FONT_SIZE_FACTORS[Math.clamp(index, 1, 7) - 1]);
            } catch (NumberFormatException e) {
                // ignore malformed size
            }
        }
        return result;
    }

    private static WhiteSpaceMode childWhiteSpace(Element element, WhiteSpaceMode current) {
        return CssStyleParser.whiteSpace(element.attr("style"), current);
    }

    /// For heading/pre content: nested blocks are squashed into one inline sequence
    /// (rare, only from malformed markup), separated by line breaks.
    private static List<Inline> flattenToInlines(List<Block> blocks) {
        List<Inline> result = new ArrayList<>();
        for (Block block : blocks) {
            if (!result.isEmpty()) {
                result.add(new Inline.LineBreak());
            }
            switch (block) {
                case Block.Paragraph(List<Inline> inlines, boolean ignoredSpaced, List<String> ignoredClasses) -> result.addAll(inlines);
                case Block.Pre(List<Inline> inlines, List<String> ignoredClasses) -> result.addAll(inlines);
                case Block.Heading(int ignoredLevel, List<Inline> inlines, List<String> ignoredClasses) -> result.addAll(inlines);
                case Block.Container(List<Block> children, List<String> ignoredClasses) -> result.addAll(flattenToInlines(children));
                default -> {
                    // lists/tables inside headings or pre: not supported, dropped
                }
            }
        }
        if (!result.isEmpty() && result.getLast() instanceof Inline.LineBreak) {
            result.removeLast();
        }
        return result;
    }

    /// Browsers ignore a single newline directly after `<pre>` and before `</pre>`.
    private static List<Inline> trimPreEdges(List<Inline> inlines) {
        if (inlines.isEmpty()) {
            return inlines;
        }
        List<Inline> result = new ArrayList<>(inlines);
        if (result.getFirst() instanceof Inline.TextRun(String text, InlineStyle style) && text.startsWith("\n")) {
            replaceOrRemove(result, 0, text.substring(1), style);
        }
        if (!result.isEmpty() && result.getLast() instanceof Inline.TextRun(String text, InlineStyle style) && text.endsWith("\n")) {
            replaceOrRemove(result, result.size() - 1, text.substring(0, text.length() - 1), style);
        }
        return result;
    }

    private static void replaceOrRemove(List<Inline> inlines, int index, String newText, InlineStyle style) {
        if (newText.isEmpty()) {
            inlines.remove(index);
        } else {
            inlines.set(index, new Inline.TextRun(newText, style));
        }
    }
}
