package org.jabref.htmltonode;

import java.util.List;

import org.jabref.htmltonode.model.Block;
import org.jabref.htmltonode.model.CssLength;
import org.jabref.htmltonode.model.Inline;
import org.jabref.htmltonode.model.InlineStyle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlToModelTest {

    private static List<Block> parse(String html) {
        return HtmlToNode.parse(html);
    }

    private static Block.Paragraph singleParagraph(String html) {
        List<Block> blocks = parse(html);
        assertEquals(1, blocks.size(), () -> "expected one block, got: " + blocks);
        return assertInstanceOf(Block.Paragraph.class, blocks.getFirst());
    }

    private static Inline.TextRun run(Block.Paragraph paragraph, int index) {
        return assertInstanceOf(Inline.TextRun.class, paragraph.inlines().get(index));
    }

    @Test
    void plainTextBecomesOneParagraph() {
        Block.Paragraph paragraph = singleParagraph("hello world");
        assertEquals(1, paragraph.inlines().size());
        assertEquals("hello world", run(paragraph, 0).text());
        assertEquals(InlineStyle.DEFAULT, run(paragraph, 0).style());
        assertFalse(paragraph.spaced());
    }

    @Test
    void boldItalicNesting() {
        Block.Paragraph paragraph = singleParagraph("a<b>b<i>bi</i></b>");
        assertEquals("a", run(paragraph, 0).text());
        Inline.TextRun bold = run(paragraph, 1);
        assertEquals("b", bold.text());
        assertTrue(bold.style().bold());
        assertFalse(bold.style().italic());
        Inline.TextRun boldItalic = run(paragraph, 2);
        assertEquals("bi", boldItalic.text());
        assertTrue(boldItalic.style().bold());
        assertTrue(boldItalic.style().italic());
    }

    @Test
    void inlineTagMapping() {
        Block.Paragraph paragraph = singleParagraph(
                "<em>em</em><u>u</u><s>s</s><code>c</code><small>sm</small>");
        assertTrue(run(paragraph, 0).style().italic());
        assertTrue(run(paragraph, 1).style().underline());
        assertTrue(run(paragraph, 2).style().strikethrough());
        assertTrue(run(paragraph, 3).style().monospace());
        assertEquals(1 / 1.2, run(paragraph, 4).style().fontScale(), 1e-9);
    }

    @Test
    void subAndSup() {
        Block.Paragraph paragraph = singleParagraph("x<sub>i</sub><sup>2</sup>");
        Inline.TextRun sub = run(paragraph, 1);
        assertEquals(InlineStyle.VerticalPosition.SUB, sub.style().verticalPosition());
        assertEquals(0.8, sub.style().fontScale(), 1e-9);
        Inline.TextRun sup = run(paragraph, 2);
        assertEquals(InlineStyle.VerticalPosition.SUPER, sup.style().verticalPosition());
    }

    @Test
    void markGetsYellowBackgroundByDefault() {
        Block.Paragraph paragraph = singleParagraph("a <mark>hit</mark> b");
        assertEquals("yellow", run(paragraph, 1).style().background());
    }

    @Test
    void jabrefSearchHighlighterMark() {
        Block.Paragraph paragraph = singleParagraph("a <mark style=\"background: orange\">hit</mark> b");
        assertEquals("orange", run(paragraph, 1).style().background());
    }

    @Test
    void citeprocSpans() {
        Block.Paragraph paragraph = singleParagraph(
                "<span style=\"font-variant: small-caps\">Kopp</span>"
                        + "<span style=\"font-weight: 100\">light</span>"
                        + "<span style=\"font-style: italic\">it<span style=\"font-style: normal\">reset</span></span>");
        assertTrue(run(paragraph, 0).style().smallCaps());
        assertEquals(100, run(paragraph, 1).style().fontWeight());
        assertTrue(run(paragraph, 2).style().italic());
        assertFalse(run(paragraph, 3).style().italic());
    }

    @Test
    void linksResolveAgainstBaseUri() {
        List<Block> blocks = HtmlToNode.parse("<a href=\"sub/file.pdf\">doc</a>", "file:///tmp/library/");
        Block.Paragraph paragraph = assertInstanceOf(Block.Paragraph.class, blocks.getFirst());
        assertEquals("file:/tmp/library/sub/file.pdf", run(paragraph, 0).style().href());
    }

    @Test
    void baseHrefInsideDocumentWins() {
        List<Block> blocks = HtmlToNode.parse(
                "<html><head><base href=\"file:///data/dir/\"></head><body><a href=\"x.pdf\">x</a></body></html>", null);
        Block.Paragraph paragraph = assertInstanceOf(Block.Paragraph.class, blocks.getFirst());
        assertEquals("file:/data/dir/x.pdf", run(paragraph, 0).style().href());
    }

    @Test
    void namedAnchorIsNotALink() {
        // the default JabRef preview layout contains <a name="\citationkey">
        Block.Paragraph paragraph = singleParagraph("<a name=\"key2026\"> (key2026)</a>");
        assertNull(run(paragraph, 0).style().href());
    }

    @Test
    void fontElement() {
        Block.Paragraph paragraph = singleParagraph(
                "<font face=\"sans-serif\">a</font><font color=\"red\" size=\"5\">b</font><font size=\"+1\">c</font>");
        assertNull(run(paragraph, 0).style().fontFamily());
        assertEquals("red", run(paragraph, 1).style().color());
        assertEquals(1.5, run(paragraph, 1).style().fontScale(), 1e-9);
        assertEquals(1.125, run(paragraph, 2).style().fontScale(), 1e-9);
    }

    @Test
    void whitespaceCollapsesAcrossElements() {
        Block.Paragraph paragraph = singleParagraph("a\n   b <b>  c</b>  d");
        // runs: "a b " + bold "c" + " d" — no double spaces anywhere
        assertEquals("a b ", run(paragraph, 0).text());
        assertEquals("c", run(paragraph, 1).text());
        assertEquals(" d", run(paragraph, 2).text());
    }

    @Test
    void nbspSurvivesCollapsing() {
        Block.Paragraph paragraph = singleParagraph("a&nbsp; b");
        assertEquals("a  b", run(paragraph, 0).text());
    }

    @Test
    void entitiesDecode() {
        Block.Paragraph paragraph = singleParagraph("K&ouml;nig &amp; Sohn &ndash; 1&dollar;");
        assertEquals("König & Sohn – 1$", run(paragraph, 0).text());
    }

    @Test
    void brCreatesLineBreakAndSwallowsFollowingSpace() {
        Block.Paragraph paragraph = singleParagraph("a<BR>   b");
        assertEquals("a", run(paragraph, 0).text());
        assertInstanceOf(Inline.LineBreak.class, paragraph.inlines().get(1));
        assertEquals("b", run(paragraph, 2).text());
    }

    @Test
    void trailingLineBreaksAreDropped() {
        Block.Paragraph paragraph = singleParagraph("text<br><br>");
        assertEquals(1, paragraph.inlines().size());
    }

    @Test
    void paragraphVersusDiv() {
        List<Block> blocks = parse("<p>spaced</p><div>plain</div>");
        assertTrue(assertInstanceOf(Block.Paragraph.class, blocks.get(0)).spaced());
        assertFalse(assertInstanceOf(Block.Paragraph.class, blocks.get(1)).spaced());
    }

    @Test
    void divClassesArePreserved() {
        List<Block> blocks = parse("<div class=\"csl-entry\">text</div>");
        assertEquals(List.of("csl-entry"), assertInstanceOf(Block.Paragraph.class, blocks.getFirst()).cssClasses());
    }

    @Test
    void cslBibliographyStructure() {
        List<Block> blocks = parse("""
                <div class="csl-bib-body">
                  <div class="csl-entry">
                    <div class="csl-left-margin">[1]</div><div class="csl-right-inline">Text of entry.</div>
                  </div>
                </div>
                """);
        Block.Container bibBody = assertInstanceOf(Block.Container.class, blocks.getFirst());
        assertEquals(List.of("csl-bib-body"), bibBody.cssClasses());
        Block.Container entry = assertInstanceOf(Block.Container.class, bibBody.blocks().getFirst());
        assertEquals(List.of("csl-entry"), entry.cssClasses());
        assertEquals(2, entry.blocks().size());
        assertEquals(List.of("csl-left-margin"), assertInstanceOf(Block.Paragraph.class, entry.blocks().get(0)).cssClasses());
        assertEquals(List.of("csl-right-inline"), assertInstanceOf(Block.Paragraph.class, entry.blocks().get(1)).cssClasses());
    }

    @Test
    void interBlockWhitespaceDoesNotCreateParagraphs() {
        List<Block> blocks = parse("<div>a</div>\n   \n<div>b</div>");
        assertEquals(2, blocks.size());
    }

    @Test
    void divWithOnlyBrRendersAsEmptyLine() {
        List<Block> blocks = parse("<div>a</div><div><br></div><div>b</div>");
        assertEquals(3, blocks.size());
        Block.Paragraph middle = assertInstanceOf(Block.Paragraph.class, blocks.get(1));
        assertEquals(List.of(new Inline.LineBreak()), middle.inlines());
    }

    @Test
    void headings() {
        List<Block> blocks = parse("<h2>Title</h2>");
        Block.Heading heading = assertInstanceOf(Block.Heading.class, blocks.getFirst());
        assertEquals(2, heading.level());
        assertEquals("Title", assertInstanceOf(Inline.TextRun.class, heading.inlines().getFirst()).text());
    }

    @Test
    void unorderedAndOrderedLists() {
        List<Block> blocks = parse("<ul><li>one</li><li>two</li></ul><ol start=\"3\"><li>three</li></ol>");
        Block.ListBlock unordered = assertInstanceOf(Block.ListBlock.class, blocks.get(0));
        assertFalse(unordered.ordered());
        assertEquals(2, unordered.items().size());
        Block.ListBlock ordered = assertInstanceOf(Block.ListBlock.class, blocks.get(1));
        assertTrue(ordered.ordered());
        assertEquals(3, ordered.start());
    }

    @Test
    void nestedListInsideItem() {
        List<Block> blocks = parse("<ul><li>outer<ul><li>inner</li></ul></li></ul>");
        Block.ListBlock outer = assertInstanceOf(Block.ListBlock.class, blocks.getFirst());
        List<Block> itemBlocks = outer.items().getFirst().blocks();
        assertEquals(2, itemBlocks.size());
        assertInstanceOf(Block.Paragraph.class, itemBlocks.get(0));
        assertInstanceOf(Block.ListBlock.class, itemBlocks.get(1));
    }

    @Test
    void definitionList() {
        List<Block> blocks = parse("<dl><dt>Term</dt><dd>Detail</dd></dl>");
        Block.DefinitionList list = assertInstanceOf(Block.DefinitionList.class, blocks.getFirst());
        assertEquals(2, list.items().size());
        assertTrue(list.items().get(0).term());
        assertFalse(list.items().get(1).term());
    }

    @Test
    void blockquoteAndRule() {
        List<Block> blocks = parse("<blockquote><p>quoted</p></blockquote><hr>");
        assertInstanceOf(Block.Quote.class, blocks.get(0));
        assertInstanceOf(Block.Rule.class, blocks.get(1));
    }

    @Test
    void prePreservesWhitespaceAndIsMonospace() {
        List<Block> blocks = parse("<pre>\nline1\n  line2</pre>");
        Block.Pre pre = assertInstanceOf(Block.Pre.class, blocks.getFirst());
        Inline.TextRun content = assertInstanceOf(Inline.TextRun.class, pre.inlines().getFirst());
        // the newline right after <pre> is ignored, inner whitespace kept
        assertEquals("line1\n  line2", content.text());
        assertTrue(content.style().monospace());
    }

    @Test
    void preWrapDivPreservesWhitespace() {
        // the AI summary raw template
        List<Block> blocks = parse(
                "<div style=\"white-space: pre-wrap; word-wrap: break-word; width: 100vw\">Line1\n  Line2   spaced</div>");
        Block.Paragraph paragraph = assertInstanceOf(Block.Paragraph.class, blocks.getFirst());
        assertEquals("Line1\n  Line2   spaced", assertInstanceOf(Inline.TextRun.class, paragraph.inlines().getFirst()).text());
    }

    @Test
    void simpleTable() {
        List<Block> blocks = parse("<table><tr><th>H</th><td colspan=\"2\">D</td></tr></table>");
        Block.Table table = assertInstanceOf(Block.Table.class, blocks.getFirst());
        Block.TableRow row = table.rows().getFirst();
        assertTrue(row.cells().get(0).header());
        assertEquals(2, row.cells().get(1).columnSpan());
        // th content inherits bold
        Block.Paragraph headerContent = assertInstanceOf(Block.Paragraph.class, row.cells().get(0).blocks().getFirst());
        assertTrue(assertInstanceOf(Inline.TextRun.class, headerContent.inlines().getFirst()).style().bold());
    }

    @Test
    void imageAttributesAndCoverStyle() {
        List<Block> blocks = parse(
                "<img style=\"border-width:1px; border-style:solid; border-color:auto; display:block; height:12rem;\" src=\"file:///covers/x.png\"> <br>");
        Block.Paragraph paragraph = assertInstanceOf(Block.Paragraph.class, blocks.getFirst());
        Inline.Image image = assertInstanceOf(Inline.Image.class, paragraph.inlines().getFirst());
        assertEquals("file:/covers/x.png", image.source());
        assertTrue(image.blockImage());
        assertEquals(new CssLength(12, CssLength.Unit.EM), image.height());
        assertNull(image.width());
    }

    @Test
    void unknownInlineTagIsTransparent() {
        Block.Paragraph paragraph = singleParagraph("a<foo>b</foo>c");
        assertEquals(1, paragraph.inlines().size());
        assertEquals("abc", run(paragraph, 0).text());
    }

    @Test
    void scriptAndStyleAreSkipped() {
        List<Block> blocks = parse("<style>.x{color:red}</style><script>alert(1)</script><div>content</div>");
        assertEquals(1, blocks.size());
        assertEquals("content", assertInstanceOf(Inline.TextRun.class,
                assertInstanceOf(Block.Paragraph.class, blocks.getFirst()).inlines().getFirst()).text());
    }

    @Test
    void previewViewerWrapperParses() {
        // exactly the wrapper PreviewViewer.formatPreviewText produces
        List<Block> blocks = parse("""
                <html>
                    <head>
                        <base href="file:///tmp/papers/">
                    </head>
                    <body id="previewBody">
                         <div id="content"> Some <b>preview</b> with <a href="attachment.pdf">a file</a> </div>
                    </body>
                </html>
                """);
        assertEquals(1, blocks.size());
        Block.Paragraph content = assertInstanceOf(Block.Paragraph.class, blocks.getFirst());
        Inline.TextRun link = (Inline.TextRun) content.inlines().stream()
                .filter(inline -> inline instanceof Inline.TextRun textRun && textRun.style().link())
                .findFirst().orElseThrow();
        assertEquals("file:/tmp/papers/attachment.pdf", link.style().href());
    }

    @Test
    void emptyAndBlankInput() {
        assertTrue(parse("").isEmpty());
        assertTrue(parse("   \n  ").isEmpty());
    }

    @Test
    void adjacentRunsWithSameStyleAreMerged() {
        Block.Paragraph paragraph = singleParagraph("a<span>b</span><span style=\"unknown: x\">c</span>");
        assertEquals(1, paragraph.inlines().size());
        assertEquals("abc", run(paragraph, 0).text());
    }

    @Test
    void errorDivFromPreviewViewer() {
        List<Block> blocks = parse("""
                <div class="error">
                    <h3>Error while generating citation style</h3>
                    <p>Something failed.</p>
                    <p><small>Check the event logs for details.</small></p>
                </div>
                """);
        Block.Container error = assertInstanceOf(Block.Container.class, blocks.getFirst());
        assertEquals(List.of("error"), error.cssClasses());
        assertEquals(3, error.blocks().size());
        assertNotNull(assertInstanceOf(Block.Heading.class, error.blocks().getFirst()));
    }
}
