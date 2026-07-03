package org.jabref.htmltonode;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Path;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Structure-level tests: no Scene, no toolkit, works headless (see HeadlessFxSmokeTest).
class FxRendererTest {

    private static final double BASE = 12;

    private static final HtmlRenderOptions OPTIONS = HtmlRenderOptions.defaults()
            .withBaseFontSize(BASE)
            .withBaseFontFamily("System");

    private static VBox render(String html) {
        return (VBox) HtmlToNode.render(html, OPTIONS);
    }

    private static TextFlow firstFlow(String html) {
        Node first = render(html).getChildren().getFirst();
        return assertInstanceOf(TextFlow.class, first);
    }

    /// Text children of a flow, skipping the internal highlight layer group.
    private static List<Text> texts(TextFlow flow) {
        return flow.getChildren().stream()
                   .filter(Text.class::isInstance)
                   .map(Text.class::cast)
                   .toList();
    }

    @Test
    void rootHasStyleClassAndStylesheet() {
        VBox root = render("hello");
        assertTrue(root.getStyleClass().contains("html-view"));
        assertEquals(1, root.getStylesheets().size());
        assertTrue(root.getStylesheets().getFirst().endsWith("html-to-node.css"));
    }

    @Test
    void boldAndItalicFonts() {
        TextFlow flow = firstFlow("plain <b>bold</b> <i>italic</i>");
        List<Text> texts = texts(flow);
        assertEquals("plain ", texts.get(0).getText());
        assertTrue(texts.get(1).getFont().getStyle().toLowerCase().contains("bold"));
        // texts.get(2) is the collapsed space between </b> and <i>
        assertTrue(texts.get(3).getFont().getStyle().toLowerCase().contains("italic"));
        texts.forEach(text -> assertTrue(text.getStyleClass().contains("html-text")));
    }

    @Test
    void fontSizesScale() {
        TextFlow flow = firstFlow("a<font size=\"5\">big</font><small>small</small>");
        List<Text> texts = texts(flow);
        assertEquals(BASE, texts.get(0).getFont().getSize(), 1e-6);
        assertEquals(BASE * 1.5, texts.get(1).getFont().getSize(), 1e-6);
        assertEquals(BASE / 1.2, texts.get(2).getFont().getSize(), 1e-6);
    }

    @Test
    void monospaceUsesConfiguredFamily() {
        TextFlow flow = firstFlow("<code>x = 1</code>");
        assertEquals("Monospaced", texts(flow).getFirst().getFont().getFamily());
    }

    @Test
    void linkIsClickableStyledText() {
        AtomicReference<String> clicked = new AtomicReference<>();
        HtmlRenderOptions options = OPTIONS.withLinkHandler(clicked::set);
        VBox root = (VBox) HtmlToNode.render("see <a href=\"https://doi.org/10.1/x\">10.1/x</a>", options);
        TextFlow flow = (TextFlow) root.getChildren().getFirst();
        Text link = texts(flow).get(1);

        assertTrue(link.getStyleClass().contains("html-link"));
        assertTrue(link.isUnderline());
        assertNotNull(link.getOnMouseClicked());

        link.getOnMouseClicked().handle(mouseClick(MouseButton.PRIMARY));
        assertEquals("https://doi.org/10.1/x", clicked.get());

        clicked.set(null);
        link.getOnMouseClicked().handle(mouseClick(MouseButton.SECONDARY));
        assertEquals(null, clicked.get());
    }

    @Test
    void namedAnchorRendersAsPlainText() {
        TextFlow flow = firstFlow("<a name=\"key\">(key)</a>");
        Text text = texts(flow).getFirst();
        assertFalse(text.getStyleClass().contains("html-link"));
        assertFalse(text.isUnderline());
    }

    @Test
    void explicitColorIsSetAndInlined() {
        TextFlow flow = firstFlow("<font color=\"red\">warm</font>");
        Text text = texts(flow).getFirst();
        assertEquals(Color.RED, text.getFill());
        assertTrue(text.getStyle().contains("-fx-fill"));
    }

    @Test
    void subAndSupTranslateAndShrink() {
        TextFlow flow = firstFlow("x<sub>i</sub><sup>2</sup>");
        List<Text> texts = texts(flow);
        Text sub = texts.get(1);
        Text sup = texts.get(2);
        assertEquals(BASE * 0.8, sub.getFont().getSize(), 1e-6);
        assertTrue(sub.getTranslateY() > 0, "subscript moves down");
        assertTrue(sup.getTranslateY() < 0, "superscript moves up");
    }

    @Test
    void smallCapsSplitsLowercaseIntoSmallerCapitals() {
        TextFlow flow = firstFlow("<span style=\"font-variant: small-caps\">Kopp</span>");
        List<Text> texts = texts(flow);
        assertEquals(2, texts.size());
        assertEquals("K", texts.get(0).getText());
        assertEquals(BASE, texts.get(0).getFont().getSize(), 1e-6);
        assertEquals("OPP", texts.get(1).getText());
        assertEquals(BASE * 0.8, texts.get(1).getFont().getSize(), 1e-6);
    }

    @Test
    void markPaintsBackgroundShapes() {
        TextFlow flow = firstFlow("before <mark style=\"background: orange\">hit</mark> after");
        flow.resize(400, flow.prefHeight(400));
        flow.layout();

        Group layer = (Group) flow.getChildrenUnmodifiable().getFirst();
        assertFalse(layer.getChildren().isEmpty(), "highlight layer should contain shapes after layout");
        Path path = assertInstanceOf(Path.class, layer.getChildren().getFirst());
        assertEquals(Color.web("orange"), path.getFill());
        assertFalse(path.getElements().isEmpty());
    }

    @Test
    void headingsAreScaledAndBold() {
        VBox root = render("<h1>One</h1><h3>Three</h3>");
        TextFlow h1 = (TextFlow) root.getChildren().get(0);
        TextFlow h3 = (TextFlow) root.getChildren().get(1);
        assertTrue(h1.getStyleClass().contains("html-h1"));
        assertEquals(BASE * 2.0, texts(h1).getFirst().getFont().getSize(), 1e-6);
        assertTrue(texts(h1).getFirst().getFont().getStyle().toLowerCase().contains("bold"));
        assertEquals(BASE * 1.17, texts(h3).getFirst().getFont().getSize(), 1e-6);
    }

    @Test
    void spacedParagraphsGetTopMargins() {
        VBox root = render("<p>one</p><p>two</p>");
        Node first = root.getChildren().get(0);
        Node second = root.getChildren().get(1);
        assertEquals(null, VBox.getMargin(first), "first block starts flush");
        Insets margin = VBox.getMargin(second);
        assertNotNull(margin);
        assertEquals(BASE, margin.getTop(), 1e-6);
    }

    @Test
    void plainDivsHaveNoMargins() {
        VBox root = render("<div>one</div><div>two</div>");
        assertEquals(null, VBox.getMargin(root.getChildren().get(1)));
    }

    @Test
    void listsRenderMarkersAndContent() {
        VBox root = render("<ol start=\"3\"><li>three</li><li>four</li></ol>");
        VBox list = assertInstanceOf(VBox.class, root.getChildren().getFirst());
        assertTrue(list.getStyleClass().contains("html-list"));
        HBox firstRow = assertInstanceOf(HBox.class, list.getChildren().getFirst());
        HBox markerBox = assertInstanceOf(HBox.class, firstRow.getChildren().getFirst());
        Text marker = assertInstanceOf(Text.class, markerBox.getChildren().getFirst());
        assertEquals("3.", marker.getText());
        HBox secondRow = assertInstanceOf(HBox.class, list.getChildren().get(1));
        Text secondMarker = (Text) ((HBox) secondRow.getChildren().getFirst()).getChildren().getFirst();
        assertEquals("4.", secondMarker.getText());
    }

    @Test
    void tableBecomesGridPane() {
        VBox root = render("<table><tr><th>H1</th><th>H2</th></tr><tr><td>a</td><td>b</td></tr></table>");
        GridPane grid = assertInstanceOf(GridPane.class, root.getChildren().getFirst());
        assertEquals(4, grid.getChildren().size());
    }

    @Test
    void ruleRendersAsRegion() {
        VBox root = render("a<hr>b");
        Region rule = assertInstanceOf(Region.class, root.getChildren().get(1));
        assertTrue(rule.getStyleClass().contains("html-hr"));
    }

    @Test
    void coverImageGetsFitHeightFromRem() {
        VBox root = render("<img style=\"display:block; height:12rem;\" src=\"data:image/png;base64,"
                + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==\">");
        TextFlow flow = (TextFlow) root.getChildren().getFirst();
        ImageView view = flow.getChildren().stream()
                             .filter(ImageView.class::isInstance)
                             .map(ImageView.class::cast)
                             .findFirst().orElseThrow();
        assertEquals(12 * BASE, view.getFitHeight(), 1e-6);
        assertTrue(view.getStyleClass().contains("html-image"));
    }

    @Test
    void imagesCanBeDisabled() {
        VBox root = (VBox) HtmlToNode.render("<img src=\"file:///x.png\">text",
                OPTIONS.withRenderImages(false));
        TextFlow flow = (TextFlow) root.getChildren().getFirst();
        assertTrue(flow.getChildren().stream().noneMatch(ImageView.class::isInstance));
    }

    @Test
    void remoteImagesAreOffByDefault() {
        VBox root = render("<img src=\"https://example.org/x.png\">text");
        TextFlow flow = (TextFlow) root.getChildren().getFirst();
        assertTrue(flow.getChildren().stream().noneMatch(ImageView.class::isInstance));
    }

    @Test
    void cslClassesArriveAsStyleClasses() {
        VBox root = render("<div class=\"csl-bib-body\"><div class=\"csl-entry\">entry text</div></div>");
        VBox bibBody = assertInstanceOf(VBox.class, root.getChildren().getFirst());
        assertTrue(bibBody.getStyleClass().contains("csl-bib-body"));
        TextFlow entry = assertInstanceOf(TextFlow.class, bibBody.getChildren().getFirst());
        assertTrue(entry.getStyleClass().contains("csl-entry"));
    }

    @Test
    void lineBreaksBecomeNewlineTexts() {
        TextFlow flow = firstFlow("a<br>b");
        List<Text> texts = texts(flow);
        assertEquals(3, texts.size());
        assertEquals("\n", texts.get(1).getText());
    }

    private static MouseEvent mouseClick(MouseButton button) {
        return new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, button, 1,
                false, false, false, false,
                button == MouseButton.PRIMARY, false, button == MouseButton.SECONDARY,
                false, false, true, null);
    }
}
