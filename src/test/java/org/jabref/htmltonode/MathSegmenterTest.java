package org.jabref.htmltonode;

import java.util.List;

import org.jabref.htmltonode.internal.MathRendering;
import org.jabref.htmltonode.model.Block;
import org.jabref.htmltonode.model.Inline;
import org.jabref.htmltonode.model.InlineStyle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Covers TeX-span recognition ([org.jabref.htmltonode.internal.MathSegmenter], reached through
/// [HtmlToNode#parse(String, HtmlRenderOptions)] with math enabled) and the toolkit-free half of
/// [MathRendering]. Rendering the equation into a node needs the JavaFX toolkit and lives in the
/// `gui`-tagged tests.
class MathSegmenterTest {

    private static List<Inline> mathInlines(String html) {
        List<Block> blocks = HtmlToNode.parse(html, HtmlRenderOptions.defaults().withRenderMath(true));
        assertEquals(1, blocks.size(), () -> "expected one block, got: " + blocks);
        return assertInstanceOf(Block.Paragraph.class, blocks.getFirst()).inlines();
    }

    private static Inline.Math math(List<Inline> inlines, int index) {
        return assertInstanceOf(Inline.Math.class, inlines.get(index));
    }

    private static Inline.TextRun text(List<Inline> inlines, int index) {
        return assertInstanceOf(Inline.TextRun.class, inlines.get(index));
    }

    @Test
    void inlineDollarSpanIsSplitOutWithSurroundingText() {
        List<Inline> inlines = mathInlines("mass-energy $E=mc^2$ here");
        assertEquals(3, inlines.size());
        assertEquals("mass-energy ", text(inlines, 0).text());
        Inline.Math math = math(inlines, 1);
        assertEquals("E=mc^2", math.tex());
        assertFalse(math.display());
        assertEquals("$E=mc^2$", math.source());
        assertEquals(" here", text(inlines, 2).text());
    }

    @Test
    void doubleDollarIsDisplayStyle() {
        Inline.Math math = math(mathInlines("before $$a+b$$ after"), 1);
        assertEquals("a+b", math.tex());
        assertTrue(math.display());
        assertEquals("$$a+b$$", math.source());
    }

    @Test
    void parenDelimitersAreInlineStyle() {
        Inline.Math math = math(mathInlines("x \\(a+b\\) y"), 1);
        assertEquals("a+b", math.tex());
        assertFalse(math.display());
        assertEquals("\\(a+b\\)", math.source());
    }

    @Test
    void bracketDelimitersAreDisplayStyle() {
        Inline.Math math = math(mathInlines("x \\[a+b\\] y"), 1);
        assertEquals("a+b", math.tex());
        assertTrue(math.display());
        assertEquals("\\[a+b\\]", math.source());
    }

    @Test
    void mathIsNotRecognizedWithoutTheOption() {
        List<Block> blocks = HtmlToNode.parse("$E=mc^2$");
        List<Inline> inlines = assertInstanceOf(Block.Paragraph.class, blocks.getFirst()).inlines();
        assertNoMath(inlines);
        assertEquals("$E=mc^2$", text(inlines, 0).text());
    }

    @Test
    void currencyAmountsStayText() {
        // Pandoc heuristic: a digit right after the closing $ marks currency, not a closing delimiter
        assertNoMath(mathInlines("it costs $5 and $10 today"));
    }

    @Test
    void dollarAdjacentToWhitespaceIsNotADelimiter() {
        // opening $ must be followed, and the closing $ preceded, by non-whitespace
        assertNoMath(mathInlines("a $ x$ b"));
    }

    @Test
    void escapedDollarDoesNotOpenASpanAndDropsTheBackslash() {
        List<Inline> inlines = mathInlines("price \\$5 up to \\$9 each");
        assertNoMath(inlines);
        assertEquals(1, inlines.size());
        assertEquals("price $5 up to $9 each", text(inlines, 0).text());
    }

    @Test
    void mathInCodeIsLeftAlone() {
        List<Inline> inlines = mathInlines("<code>$x$</code>");
        assertNoMath(inlines);
        assertTrue(text(inlines, 0).style().monospace());
    }

    @Test
    void mathInLinkTextIsLeftAlone() {
        List<Inline> inlines = mathInlines("<a href=\"https://example.org\">$y$</a>");
        assertNoMath(inlines);
        assertTrue(text(inlines, 0).style().link());
    }

    @Test
    void plainTextExtractionRoundTripsTheSource() {
        List<Block> blocks = HtmlToNode.parse("a $x+y$ b", HtmlRenderOptions.defaults().withRenderMath(true));
        assertEquals("a $x+y$ b", HtmlToNode.toPlainText(blocks).strip());
    }

    @Test
    void isRenderableAcceptsValidTexAndRejectsBrokenTex() {
        assertTrue(MathRendering.isRenderable(new Inline.Math("\\frac{1}{2}", false, "$\\frac{1}{2}$", InlineStyle.DEFAULT)));
        // an unknown command makes JLaTeXMath throw during parsing; the renderer then falls back to source
        assertFalse(MathRendering.isRenderable(new Inline.Math("\\nosuchcommand", false, "$\\nosuchcommand$", InlineStyle.DEFAULT)));
    }

    private static void assertNoMath(List<Inline> inlines) {
        assertTrue(inlines.stream().noneMatch(inline -> inline instanceof Inline.Math),
                () -> "expected no math span, got: " + inlines);
    }
}
