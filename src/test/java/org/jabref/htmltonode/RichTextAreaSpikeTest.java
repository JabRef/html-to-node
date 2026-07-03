package org.jabref.htmltonode;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

import jfx.incubator.scene.control.richtext.RichTextArea;
import jfx.incubator.scene.control.richtext.model.SimpleViewOnlyStyledModel;
import jfx.incubator.scene.control.richtext.model.StyleAttribute;
import jfx.incubator.scene.control.richtext.model.StyleAttributeMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Phase-0 spike for the RichTextArea renderer (see GitHub issue #2): verifies that the
/// incubator artifact resolves, the styled model API works as planned (segments, highlights,
/// custom attributes), and a skin can be instantiated with the toolkit running.
///
/// Needs the JavaFX toolkit — run via `./gradlew guiTest` with a display or under `xvfb-run`.
@Tag("gui")
class RichTextAreaSpikeTest {

    private static final StyleAttribute<String> HREF = new StyleAttribute<>("HREF", String.class, false);

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

    private static <T> T onFxThread(java.util.concurrent.Callable<T> action) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(action.call());
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
        return result.get();
    }

    private static SimpleViewOnlyStyledModel citationModel() {
        SimpleViewOnlyStyledModel model = new SimpleViewOnlyStyledModel();
        model.addSegment("Kopp, O.: ")
             .addSegment("Some Paper", StyleAttributeMap.builder().setItalic(true).build())
             .highlight(0, 4, Color.ORANGE)
             .nl()
             .addSegment("doi.org/10.1/x", StyleAttributeMap.builder()
                                                            .setUnderline(true)
                                                            .set(HREF, "https://doi.org/10.1/x")
                                                            .build());
        return model;
    }

    @Test
    void styledModelCarriesSegmentsHighlightsAndCustomAttributes() {
        SimpleViewOnlyStyledModel model = citationModel();

        assertEquals(2, model.size());
        assertEquals("Kopp, O.: Some Paper", model.getPlainText(0));
        assertEquals("doi.org/10.1/x", model.getPlainText(1));

        StyleAttributeMap linkAttrs = model.getStyleAttributeMap(null, jfx.incubator.scene.control.richtext.TextPos.ofLeading(1, 3));
        assertEquals("https://doi.org/10.1/x", linkAttrs.get(HREF));
        assertEquals(Boolean.TRUE, linkAttrs.get(StyleAttributeMap.UNDERLINE));
    }

    @Test
    void richTextAreaSkinInstantiatesWithModel() throws Exception {
        boolean skinCreated = onFxThread(() -> {
            RichTextArea area = new RichTextArea(citationModel());
            area.setEditable(false);
            new Scene(new StackPane(area), 400, 300);
            area.applyCss();
            return area.getSkin() != null;
        });
        assertTrue(skinCreated, "RichTextArea skin should instantiate under the toolkit");
        assertNotNull(HREF.getName());
    }
}
