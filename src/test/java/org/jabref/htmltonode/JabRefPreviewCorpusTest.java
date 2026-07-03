package org.jabref.htmltonode;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import org.jabref.htmltonode.model.Block;
import org.jabref.htmltonode.model.Inline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// End-to-end checks against HTML shaped exactly like what reaches JabRef's PreviewViewer:
/// the default text-based preview layout, citeproc-java CSL output (IEEE/APA-ish),
/// flexmark markdown (comment fields, AI summaries), and the search-highlighted variant.
class JabRefPreviewCorpusTest {

    private static final HtmlRenderOptions OPTIONS = HtmlRenderOptions.defaults()
            .withBaseFontSize(12)
            .withRenderImages(false);

    /// Output shape of TextBasedPreviewLayout.DEFAULT for an article-like entry.
    private static final String DEFAULT_LAYOUT_OUTPUT = """
            <font face="sans-serif">
            <b>InCollection</b><a name="Kopp2026"> (Kopp2026)</a>
            <BR><BR>Kopp, Oliver / Mustermann, Max
            <BR><b>A Great Title with K&ouml;nig &amp; Sons</b>\s
            <BR>2026, 2. edition
            <BR><i>Proceedings of Things</i>\s
            <BR>Springer: Berlin\s
            <BR> p. 11&ndash;22
            <BR>doi <a href="https://doi.org/10.1000/xyz">10.1000/xyz</a>
            <BR><BR><b>Abstract: </b>Some abstract text.\s
            <BR><BR><b>Comment: </b><p>Markdown <strong>bold</strong> and a <a href="https://jabref.org">link</a></p>
            </font>
            """;

    /// citeproc-java 3.5 HTML output for the default ieee.csl (second-field-align layout).
    private static final String IEEE_CSL_OUTPUT = """
            <div class="csl-bib-body">
              <div class="csl-entry">
                <div class="csl-left-margin">[1]</div><div class="csl-right-inline">O. Kopp and M. Mustermann, &ldquo;A great title,&rdquo; <span style="font-style: italic">Journal of Things</span>, vol. 1, no. 2, pp. 11&ndash;22, 2026, doi: <a href="https://doi.org/10.1000/xyz">10.1000/xyz</a>.</div>
              </div>
            </div>
            """;

    private static final String SMALL_CAPS_CSL_OUTPUT = """
            <div class="csl-bib-body">
              <div class="csl-entry"><span style="font-variant: small-caps">Kopp</span>, O. <span style="font-weight: bold">2026</span>. E = mc<sup>2</sup> and H<sub>2</sub>O.</div>
            </div>
            """;

    @Test
    void defaultLayoutOutputParsesAndRenders() {
        List<Block> blocks = HtmlToNode.parse(DEFAULT_LAYOUT_OUTPUT);
        assertFalse(blocks.isEmpty());

        String plainText = HtmlToNode.toPlainText(blocks);
        assertTrue(plainText.contains("InCollection (Kopp2026)"));
        assertTrue(plainText.contains("Kopp, Oliver / Mustermann, Max"));
        assertTrue(plainText.contains("A Great Title with König & Sons"));
        assertTrue(plainText.contains("p. 11–22"));
        assertTrue(plainText.contains("Markdown bold and a link"));

        Region rendered = HtmlToNode.render(blocks, OPTIONS);
        List<Text> texts = allTexts(rendered);
        assertTrue(texts.stream().anyMatch(text -> "InCollection".equals(text.getText())
                && text.getFont().getStyle().toLowerCase().contains("bold")));
        // the named anchor must not be a link, the doi and comment links must be
        assertTrue(texts.stream().noneMatch(text -> text.getText().contains("(Kopp2026)")
                && text.getStyleClass().contains("html-link")));
        assertEquals(2, texts.stream().filter(text -> text.getStyleClass().contains("html-link")).count());
    }

    @Test
    void ieeeCslOutputKeepsStructureAndStyles() {
        List<Block> blocks = HtmlToNode.parse(IEEE_CSL_OUTPUT);
        Block.Container bibBody = assertInstanceOf(Block.Container.class, blocks.getFirst());
        assertEquals(List.of("csl-bib-body"), bibBody.cssClasses());

        String plainText = HtmlToNode.toPlainText(blocks);
        assertTrue(plainText.contains("[1]"));
        assertTrue(plainText.contains("“A great title,”"));
        assertTrue(plainText.contains("pp. 11–22"));

        Region rendered = HtmlToNode.render(blocks, OPTIONS);
        List<Text> texts = allTexts(rendered);
        assertTrue(texts.stream().anyMatch(text -> "Journal of Things".equals(text.getText())
                && text.getFont().getStyle().toLowerCase().contains("italic")));
        assertTrue(texts.stream().anyMatch(text -> "10.1000/xyz".equals(text.getText())
                && text.getStyleClass().contains("html-link")));
    }

    @Test
    void smallCapsAndVerticalAlignFromCsl() {
        Region rendered = HtmlToNode.render(SMALL_CAPS_CSL_OUTPUT, OPTIONS);
        List<Text> texts = allTexts(rendered);
        // "Kopp" in small caps → "K" + "OPP" (smaller)
        assertTrue(texts.stream().anyMatch(text -> "OPP".equals(text.getText())
                && text.getFont().getSize() < 12));
        assertTrue(texts.stream().anyMatch(text -> "2026".equals(text.getText())
                && text.getFont().getStyle().toLowerCase().contains("bold")));
        assertTrue(texts.stream().anyMatch(text -> "2".equals(text.getText()) && text.getTranslateY() < 0));
        assertTrue(texts.stream().anyMatch(text -> "2".equals(text.getText()) && text.getTranslateY() > 0));
    }

    @Test
    void searchHighlightedPreviewPaintsMarks() {
        // Highlighter.highlightHtml wraps matches like this (and jsoup-normalizes the document)
        String highlighted = """
                <html><head></head><body id="previewBody">
                <div id="content">Systems <mark style="background: orange">engineering</mark> in practice</div>
                </body></html>
                """;
        Region rendered = HtmlToNode.render(highlighted, OPTIONS);
        TextFlow flow = (TextFlow) ((VBox) rendered).getChildren().getFirst();
        flow.resize(500, flow.prefHeight(500));
        flow.layout();
        Group highlightLayer = (Group) flow.getChildrenUnmodifiable().getFirst();
        assertFalse(highlightLayer.getChildren().isEmpty(), "search hit should get a painted background");
    }

    @Test
    void flexmarkCommentOutputRenders() {
        String flexmarkOutput = """
                <h2>Notes</h2>
                <p>Some <em>emphasis</em> and <strong>strong</strong> text with <code>inline code</code>.</p>
                <ul>
                <li>First point</li>
                <li>Second point with <a href="https://example.org">reference</a></li>
                </ul>
                <blockquote>
                <p>Quoted insight.</p>
                </blockquote>
                <pre><code class="language-java">int answer = 42;
                return answer;
                </code></pre>
                <hr />
                <p>Done.</p>
                """;
        List<Block> blocks = HtmlToNode.parse(flexmarkOutput);
        assertTrue(blocks.stream().anyMatch(Block.Heading.class::isInstance));
        assertTrue(blocks.stream().anyMatch(Block.ListBlock.class::isInstance));
        assertTrue(blocks.stream().anyMatch(Block.Quote.class::isInstance));
        assertTrue(blocks.stream().anyMatch(Block.Pre.class::isInstance));
        assertTrue(blocks.stream().anyMatch(Block.Rule.class::isInstance));

        Block.Pre pre = blocks.stream().filter(Block.Pre.class::isInstance).map(Block.Pre.class::cast).findFirst().orElseThrow();
        Inline.TextRun code = assertInstanceOf(Inline.TextRun.class, pre.inlines().getFirst());
        assertTrue(code.style().monospace());
        assertTrue(code.text().contains("int answer = 42;\n"));

        String plainText = HtmlToNode.toPlainText(blocks);
        assertTrue(plainText.contains("• First point"));

        Region rendered = HtmlToNode.render(blocks, OPTIONS);
        assertFalse(((VBox) rendered).getChildren().isEmpty());
    }

    @Test
    void aiSummaryRawTemplatePreservesFormatting() {
        String raw = """
                <body style="margin: 0; padding: 5px; width: 100vw"><div style="white-space: pre-wrap; word-wrap: break-word; width: 100vw">First line
                  indented second line

                last line</div></body>
                """;
        List<Block> blocks = HtmlToNode.parse(raw);
        Block.Paragraph paragraph = assertInstanceOf(Block.Paragraph.class, blocks.getFirst());
        String text = assertInstanceOf(Inline.TextRun.class, paragraph.inlines().getFirst()).text();
        assertTrue(text.contains("First line\n  indented second line\n\nlast line"));
    }

    @Test
    void bstPreviewPlainTextPassesThrough() {
        // BstPreviewLayout output is essentially plain text
        String bst = "Kopp, O. A great title. Journal of Things, 2026.";
        List<Block> blocks = HtmlToNode.parse(bst);
        assertEquals(1, blocks.size());
        assertEquals(bst, HtmlToNode.toPlainText(blocks));
    }

    @Test
    void multipleCslEntriesSeparatedByBr() {
        // CitationStyleOutputFormat.HTML joins entries with newline + <br> + newline
        String multi = """
                <div class="csl-bib-body">
                  <div class="csl-entry">First entry.</div>
                </div>
                <br>
                <div class="csl-bib-body">
                  <div class="csl-entry">Second entry.</div>
                </div>
                """;
        List<Block> blocks = HtmlToNode.parse(multi);
        long bodies = blocks.stream().filter(Block.Container.class::isInstance).count();
        assertEquals(2, bodies);
        String plainText = HtmlToNode.toPlainText(blocks);
        assertTrue(plainText.contains("First entry."));
        assertTrue(plainText.contains("Second entry."));
    }

    private static List<Text> allTexts(Node node) {
        List<Text> result = new ArrayList<>();
        collectTexts(node, result);
        return result;
    }

    private static void collectTexts(Node node, List<Text> result) {
        if (node instanceof Text text) {
            result.add(text);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectTexts(child, result);
            }
        }
    }
}
