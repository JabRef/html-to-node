package org.jabref.htmltonode;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.text.Text;

import org.jabref.htmltonode.rich.HtmlRichTextArea;
import org.jabref.htmltonode.rich.RichTextRenderer;

import jfx.incubator.scene.control.richtext.model.StyleAttributeMap;
import jfx.incubator.scene.control.richtext.skin.CellContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies that [HtmlRichTextArea] gives link runs the hand cursor. Constructing the incubator
/// control needs the JavaFX toolkit, so this is a `gui` test — run via `./gradlew guiTest` with a
/// display or under `xvfb-run`.
@Tag("gui")
class HtmlRichTextAreaTest {

    @BeforeAll
    static void startToolkit() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyRunning) {
            started.countDown();
        }
        assertTrue(started.await(15, TimeUnit.SECONDS), "JavaFX toolkit failed to start");
    }

    /// Captures the inline style the skin's cell context would apply to a segment node — the same
    /// path [jfx.incubator.scene.control.richtext.StyleHandlerRegistry#process] drives per attribute.
    private static final class CapturingCellContext implements CellContext {
        private final Node node;
        private final StringBuilder style = new StringBuilder();

        CapturingCellContext(Node node) {
            this.node = node;
        }

        @Override
        public void addStyle(String s) {
            style.append(s);
        }

        @Override
        public Node getNode() {
            return node;
        }

        @Override
        public StyleAttributeMap getAttributes() {
            return StyleAttributeMap.builder().build();
        }

        String style() {
            return style.toString();
        }
    }

    @Test
    void linkRunGetsHandCursorWhileInheritedHandlersStillApply() throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<String> linkStyle = new AtomicReference<>();
        AtomicReference<String> boldStyle = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                HtmlRichTextArea area = new HtmlRichTextArea();

                CapturingCellContext link = new CapturingCellContext(new Text("10.1/x"));
                area.getStyleHandlerRegistry()
                    .process(area, false, link, RichTextRenderer.HREF, "https://doi.org/10.1/x");
                linkStyle.set(link.style());

                // The default handlers (here: bold) must survive the registry rebuild.
                CapturingCellContext bold = new CapturingCellContext(new Text("bold"));
                area.getStyleHandlerRegistry()
                    .process(area, false, bold, StyleAttributeMap.BOLD, Boolean.TRUE);
                boldStyle.set(bold.style());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(15, TimeUnit.SECONDS), "FX task timed out");
        if (error.get() != null) {
            throw new AssertionError("FX task failed", error.get());
        }
        assertTrue(linkStyle.get().contains("-fx-cursor:hand"),
                "link run should request the hand cursor, was: " + linkStyle.get());
        assertFalse(boldStyle.get().isEmpty(), "inherited bold handler should still apply a style");
        assertFalse(boldStyle.get().contains("-fx-cursor"), "non-link run must not get the hand cursor");
    }
}
