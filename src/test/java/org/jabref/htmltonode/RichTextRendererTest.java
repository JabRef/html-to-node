package org.jabref.htmltonode;

import org.jabref.htmltonode.rich.RichTextRenderer;

import jfx.incubator.scene.control.richtext.TextPos;
import jfx.incubator.scene.control.richtext.model.StyleAttributeMap;
import jfx.incubator.scene.control.richtext.model.StyledTextModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Model-level tests for the RichTextArea renderer. Building the styled model does not need
/// the JavaFX toolkit, so these run headless like the rest of the suite.
class RichTextRendererTest {

    private static final double BASE = 12;

    private static final HtmlRenderOptions OPTIONS = HtmlRenderOptions.defaults()
            .withBaseFontSize(BASE)
            .withBaseFontFamily("System");

    private static StyledTextModel model(String html) {
        return RichTextRenderer.buildModel(HtmlToNode.parse(html), OPTIONS);
    }

    private static StyleAttributeMap attributesAt(StyledTextModel model, int paragraph, int offset) {
        return model.getStyleAttributeMap(null, TextPos.ofLeading(paragraph, offset));
    }

    @Test
    void paragraphTextAndBoldItalic() {
        StyledTextModel model = model("plain <b>bold</b> <i>italic</i>");

        assertEquals("plain bold italic", model.getPlainText(0));
        assertNull(attributesAt(model, 0, 1).get(StyleAttributeMap.BOLD));
        assertEquals(Boolean.TRUE, attributesAt(model, 0, 7).get(StyleAttributeMap.BOLD));
        assertEquals(Boolean.TRUE, attributesAt(model, 0, 12).get(StyleAttributeMap.ITALIC));
        assertEquals(BASE, attributesAt(model, 0, 1).get(StyleAttributeMap.FONT_SIZE));
    }

    @Test
    void linkCarriesHrefAttributeAndStyling() {
        StyledTextModel model = model("see <a href=\"https://doi.org/10.1/x\">10.1/x</a>");

        StyleAttributeMap linkAttributes = attributesAt(model, 0, 5);
        assertEquals("https://doi.org/10.1/x", linkAttributes.get(RichTextRenderer.HREF));
        assertEquals(Boolean.TRUE, linkAttributes.get(StyleAttributeMap.UNDERLINE));
        assertNotNull(linkAttributes.get(StyleAttributeMap.TEXT_COLOR));
        assertNull(attributesAt(model, 0, 1).get(RichTextRenderer.HREF));
    }

    @Test
    void headingIsScaledAndBold() {
        StyledTextModel model = model("<h1>Title</h1>text");

        assertEquals("Title", model.getPlainText(0));
        StyleAttributeMap headingAttributes = attributesAt(model, 0, 1);
        assertEquals(Boolean.TRUE, headingAttributes.get(StyleAttributeMap.BOLD));
        assertEquals(BASE * 2.0, headingAttributes.get(StyleAttributeMap.FONT_SIZE));
        assertEquals("text", model.getPlainText(1));
    }

    @Test
    void paragraphsBecomeSeparateModelParagraphs() {
        StyledTextModel model = model("<p>one</p><p>two</p>");

        assertEquals("one", model.getPlainText(0));
        assertEquals("two", model.getPlainText(1));
        StyleAttributeMap secondParagraph = model.getParagraph(1).getParagraphAttributes();
        assertEquals(BASE, secondParagraph.get(StyleAttributeMap.SPACE_ABOVE));
    }

    @Test
    void lineBreaksSplitParagraphs() {
        StyledTextModel model = model("a<br>b");

        assertEquals("a", model.getPlainText(0));
        assertEquals("b", model.getPlainText(1));
    }

    @Test
    void consecutiveLineBreaksKeepTheBlankLineBetweenThem() {
        StyledTextModel model = model("a<br><br>b");

        assertEquals(3, model.size());
        assertEquals("a", model.getPlainText(0));
        assertEquals("", model.getPlainText(1));
        assertEquals("b", model.getPlainText(2));
    }

    @Test
    void preservedNewlinesInPreSplitParagraphs() {
        StyledTextModel model = model("<pre>x = 1\ny = 2</pre>");

        assertEquals("x = 1", model.getPlainText(0));
        assertEquals("y = 2", model.getPlainText(1));
        assertEquals("Monospaced", attributesAt(model, 0, 1).get(StyleAttributeMap.FONT_FAMILY));
    }

    @Test
    void orderedListGetsMarkersAndIndent() {
        StyledTextModel model = model("<ol start=\"3\"><li>three</li><li>four</li></ol>");

        assertEquals("3. three", model.getPlainText(0));
        assertEquals("4. four", model.getPlainText(1));
        assertTrue(model.getParagraph(0).getParagraphAttributes().get(StyleAttributeMap.SPACE_LEFT) > 0);
    }

    @Test
    void blockquoteIsIndented() {
        StyledTextModel model = model("<blockquote>quoted</blockquote>");

        assertEquals("quoted", model.getPlainText(0));
        assertEquals(2 * BASE, model.getParagraph(0).getParagraphAttributes().get(StyleAttributeMap.SPACE_LEFT));
    }

    @Test
    void markHighlightDoesNotThrowAndKeepsText(){
        StyledTextModel model = model("before <mark style=\"background: orange\">hit</mark> after");

        assertEquals("before hit after", model.getPlainText(0));
    }

    @Test
    void smallCapsAreEmulated() {
        StyledTextModel model = model("<span style=\"font-variant: small-caps\">Kopp</span>");

        assertEquals("KOPP", model.getPlainText(0));
        assertEquals(BASE, attributesAt(model, 0, 0).get(StyleAttributeMap.FONT_SIZE));
        assertEquals(BASE * 0.8, attributesAt(model, 0, 2).get(StyleAttributeMap.FONT_SIZE));
    }

    @Test
    void blockImageBecomesEmbeddedParagraph() {
        StyledTextModel model = model("<img style=\"display:block; height:12rem;\" src=\"data:image/png;base64,"
                + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==\"> <br>text");

        assertEquals("text", model.getPlainText(model.size() - 1));
        assertTrue(model.size() >= 2, "expected an embedded image paragraph before the text");
    }

    @Test
    void hrefAtImagePositionIsNullAndDoesNotThrow() {
        // Clicking a book-cover / inline image resolves to a node segment, for which the model
        // returns a null attribute map. hrefAt must tolerate that (regression for an NPE that
        // dereferenced getStyleAttributeMap(...) on image clicks); offset 1 is the image node.
        StyledTextModel model = model("a<img src=\"data:image/png;base64,"
                + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==\">b");

        assertEquals("a b", model.getPlainText(0));
        assertNull(attributesAt(model, 0, 1), "image node segment carries no character attribute map");
        assertNull(RichTextRenderer.hrefAt(model, TextPos.ofLeading(0, 1)));
    }

    @Test
    void hrefAtLinkPositionReturnsTarget() {
        StyledTextModel model = model("see <a href=\"https://doi.org/10.1/x\">10.1/x</a>");

        assertEquals("https://doi.org/10.1/x", RichTextRenderer.hrefAt(model, TextPos.ofLeading(0, 5)));
        assertNull(RichTextRenderer.hrefAt(model, TextPos.ofLeading(0, 1)));
    }

    @Test
    void cslEntryRendersAsStyledCitation() {
        StyledTextModel model = model("""
                <div class="csl-bib-body"><div class="csl-entry">
                [1] O. Kopp, <i>Some Paper</i>, 2026.
                </div></div>""");

        assertEquals("[1] O. Kopp, Some Paper, 2026.", model.getPlainText(0).strip());
    }
}
