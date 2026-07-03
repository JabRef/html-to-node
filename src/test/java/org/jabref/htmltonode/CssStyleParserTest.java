package org.jabref.htmltonode;

import java.util.Optional;

import org.jabref.htmltonode.internal.CssStyleParser;
import org.jabref.htmltonode.model.CssLength;
import org.jabref.htmltonode.model.InlineStyle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CssStyleParserTest {

    private static InlineStyle apply(String css) {
        return CssStyleParser.apply(InlineStyle.DEFAULT, css);
    }

    @Test
    void fontStyleItalicAndOblique() {
        assertTrue(apply("font-style: italic").italic());
        assertTrue(apply("font-style: oblique").italic());
        assertFalse(CssStyleParser.apply(InlineStyle.DEFAULT.withItalic(true), "font-style: normal").italic());
    }

    @Test
    void fontWeightKeywordsAndNumbers() {
        assertEquals(700, apply("font-weight: bold").fontWeight());
        // citeproc-java emits font-weight: 100 for "light"
        assertEquals(100, apply("font-weight: 100").fontWeight());
        assertEquals(400, CssStyleParser.apply(InlineStyle.DEFAULT.withFontWeight(700), "font-weight: normal").fontWeight());
    }

    @Test
    void smallCapsFromCiteproc() {
        assertTrue(apply("font-variant: small-caps").smallCaps());
        assertFalse(CssStyleParser.apply(InlineStyle.DEFAULT.withSmallCaps(true), "font-variant: normal").smallCaps());
    }

    @Test
    void textDecoration() {
        assertTrue(apply("text-decoration: underline").underline());
        assertTrue(apply("text-decoration: line-through").strikethrough());
        InlineStyle both = apply("text-decoration: underline line-through");
        assertTrue(both.underline());
        assertTrue(both.strikethrough());
        InlineStyle none = CssStyleParser.apply(InlineStyle.DEFAULT.withUnderline(true).withStrikethrough(true), "text-decoration: none");
        assertFalse(none.underline());
        assertFalse(none.strikethrough());
    }

    @Test
    void colorAndBackground() {
        assertEquals("#ff0000", apply("color: #ff0000").color());
        // the JabRef search highlighter emits exactly this
        assertEquals("orange", apply("background: orange").background());
        assertEquals("yellow", apply("background-color: yellow").background());
    }

    @ParameterizedTest
    @CsvSource({
            "'font-size: 2em', 2.0",
            "'font-size: 150%', 1.5",
            "'font-size: 32px', 2.0",
            "'font-size: 12pt', 1.0",
            "'font-size: medium', 1.0",
            "'font-size: xx-large', 2.0",
    })
    void fontSize(String css, double expectedScale) {
        assertEquals(expectedScale, apply(css).fontScale(), 1e-9);
    }

    @Test
    void fontSizeEmIsRelativeToCurrent() {
        InlineStyle doubled = CssStyleParser.apply(InlineStyle.DEFAULT.withFontScale(2.0), "font-size: 0.5em");
        assertEquals(1.0, doubled.fontScale(), 1e-9);
    }

    @Test
    void fontFamily() {
        assertEquals("Times New Roman", apply("font-family: \"Times New Roman\", serif").fontFamily());
        assertTrue(apply("font-family: monospace").monospace());
        // generic sans-serif keeps the renderer default
        assertEquals(InlineStyle.DEFAULT.fontFamily(), apply("font-family: sans-serif").fontFamily());
    }

    @Test
    void verticalAlign() {
        assertEquals(InlineStyle.VerticalPosition.SUB, apply("vertical-align: sub").verticalPosition());
        assertEquals(InlineStyle.VerticalPosition.SUPER, apply("vertical-align: super").verticalPosition());
    }

    @Test
    void unknownPropertiesAndMalformedInputAreIgnored() {
        assertEquals(InlineStyle.DEFAULT, apply("border-width: 1px; display: block; nonsense"));
        assertEquals(InlineStyle.DEFAULT, apply(";;;:::"));
        assertEquals(InlineStyle.DEFAULT, apply("font-weight: heavyish"));
    }

    @Test
    void whiteSpaceMode() {
        assertEquals(org.jabref.htmltonode.internal.WhiteSpaceMode.PRESERVE,
                CssStyleParser.whiteSpace("white-space: pre-wrap", org.jabref.htmltonode.internal.WhiteSpaceMode.NORMAL));
        assertEquals(org.jabref.htmltonode.internal.WhiteSpaceMode.NORMAL,
                CssStyleParser.whiteSpace("white-space: normal", org.jabref.htmltonode.internal.WhiteSpaceMode.PRESERVE));
        assertEquals(org.jabref.htmltonode.internal.WhiteSpaceMode.NORMAL,
                CssStyleParser.whiteSpace("color: red", org.jabref.htmltonode.internal.WhiteSpaceMode.NORMAL));
    }

    @Test
    void lengths() {
        // the JabRef cover image uses height: 12rem
        assertEquals(Optional.of(new CssLength(12, CssLength.Unit.EM)),
                CssStyleParser.length("border-width:1px; display:block; height:12rem;", "height"));
        assertEquals(Optional.of(new CssLength(24, CssLength.Unit.PX)), CssStyleParser.parseLength("24px"));
        assertEquals(Optional.of(new CssLength(16, CssLength.Unit.PX)), CssStyleParser.parseLength("12pt"));
        assertEquals(Optional.of(new CssLength(100, CssLength.Unit.PX)), CssStyleParser.parseLength("100"));
        assertEquals(Optional.empty(), CssStyleParser.parseLength("auto"));
    }

    @Test
    void displayBlock() {
        assertTrue(CssStyleParser.isDisplayBlock("border-width:1px; display:block; height:12rem;"));
        assertFalse(CssStyleParser.isDisplayBlock("height:12rem"));
        assertFalse(CssStyleParser.isDisplayBlock(null));
    }
}
