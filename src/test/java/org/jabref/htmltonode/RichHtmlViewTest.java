package org.jabref.htmltonode;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.scene.Scene;

import org.jabref.htmltonode.rich.RichHtmlView;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Needs the JavaFX toolkit — run via `./gradlew guiTest` with a display or under `xvfb-run`.
@Tag("gui")
class RichHtmlViewTest {

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

    @Test
    void selectionFacadeReturnsSelectedText() throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<java.util.Optional<String>> before = new AtomicReference<>();
        AtomicReference<java.util.Optional<String>> after = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                RichHtmlView view = new RichHtmlView();
                view.setHtml("<p>First entry</p><p>Second one</p>");
                new Scene(view, 400, 300);
                view.applyCss();
                before.set(view.getSelectedText());
                view.getRichTextArea().selectAll();
                after.set(view.getSelectedText());
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
        assertEquals(java.util.Optional.empty(), before.get());
        assertEquals(java.util.Optional.of("First entry\nSecond one"), after.get());
    }

    @Test
    void viewRendersHtmlIntoRichTextArea() throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<String> firstParagraph = new AtomicReference<>();
        AtomicReference<Boolean> skinCreated = new AtomicReference<>(false);
        CountDownLatch done = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                RichHtmlView view = new RichHtmlView();
                view.setHtml("<b>Kopp, O.</b>: <i>Some Paper</i>");
                new Scene(view, 400, 300);
                view.applyCss();
                view.getRichTextArea().applyCss();
                firstParagraph.set(view.getRichTextArea().getModel().getPlainText(0));
                skinCreated.set(view.getRichTextArea().getSkin() != null);
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
        assertEquals("Kopp, O.: Some Paper", firstParagraph.get());
        assertTrue(skinCreated.get(), "RichTextArea skin should instantiate");
        assertNotNull(firstParagraph.get());
    }
}
