package org.jabref.htmltonode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import org.jabref.htmltonode.rich.RichHtmlView;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Regression test for the link-click hit test: only a click that lands on the link's glyphs opens
/// it. `getTextPosition` snaps any coordinate to the nearest caret position, so a click in the empty
/// area past a trailing link maps back onto the link — it must stay inert because the pick landed on
/// a non-text node. Needs a shown Stage so the RichTextArea skin can map screen coordinates, hence
/// `gui` — run via `./gradlew guiTest` with a display or under `xvfb-run`.
@Tag("gui")
class RichTextRendererLinkClickTest {

    private static final String HREF = "http://example.org/x";
    private static final String HTML = "<p>Trailing <a href=\"" + HREF + "\">link</a></p>";

    @BeforeAll
    static void startToolkit() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyRunning) {
            started.countDown();
        }
        assertTrue(started.await(15, TimeUnit.SECONDS), "JavaFX toolkit failed to start");
        // This test is the only one that shows and hides a Stage; without this, hiding the last window
        // tears down the shared toolkit and every later gui test class times out in Platform.runLater.
        Platform.setImplicitExit(false);
    }

    @Test
    void onlyGlyphClickOpensTrailingLink() throws Exception {
        List<String> opened = new CopyOnWriteArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<Stage> stage = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                RichHtmlView view = new RichHtmlView();
                view.setOptions(view.getOptions().withLinkHandler(opened::add));
                view.setHtml(HTML);

                Stage window = new Stage();
                stage.set(window);
                // Ample height so there is empty area below the single short line.
                window.setScene(new Scene(view, 600, 400));
                window.show();
                view.applyCss();
                view.layout();

                Text linkNode = findText(view, "link");
                assertNotNull(linkNode, "the link run should be laid out as a Text node");
                Bounds linkOnScreen = linkNode.localToScreen(linkNode.getBoundsInLocal());

                Node area = view.getRichTextArea();

                // On the glyphs: the pick lands on the link Text node -> opens.
                click(area, linkNode, linkOnScreen.getCenterX(), linkOnScreen.getCenterY());

                // Far below the line, on empty area: getTextPosition still snaps to the trailing link,
                // but the pick lands on the RichTextArea itself (no glyph) -> must stay inert.
                click(area, area, linkOnScreen.getCenterX(), linkOnScreen.getMaxY() + 150);
            } catch (Throwable t) {
                error.set(t);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(20, TimeUnit.SECONDS), "FX task timed out");
        hide(stage.get());
        if (error.get() != null) {
            throw new AssertionError("FX task failed", error.get());
        }
        assertEquals(List.of(HREF), opened, "only the on-glyph click should open the link");
    }

    /// Fires a primary click whose pick result intersects `picked` at the given screen point, mirroring
    /// how the skin reports the node under the cursor (a Text run for glyphs, the control for empty area).
    private static void click(Node dispatchTarget, Node picked, double screenX, double screenY) {
        PickResult pick = new PickResult(picked, screenX, screenY);
        MouseEvent event = new MouseEvent(MouseEvent.MOUSE_CLICKED,
                0, 0, screenX, screenY, MouseButton.PRIMARY, 1,
                false, false, false, false,
                true, false, false,
                false, false, true, pick);
        Event.fireEvent(dispatchTarget, event);
    }

    private static Text findText(Parent root, String text) {
        List<Node> queue = new ArrayList<>(root.getChildrenUnmodifiable());
        for (int i = 0; i < queue.size(); i++) {
            Node node = queue.get(i);
            if (node instanceof Text t && text.equals(t.getText())) {
                return t;
            }
            if (node instanceof Parent parent) {
                queue.addAll(parent.getChildrenUnmodifiable());
            }
        }
        return null;
    }

    private static void hide(Stage window) throws InterruptedException {
        if (window == null) {
            return;
        }
        CountDownLatch hidden = new CountDownLatch(1);
        Platform.runLater(() -> {
            window.hide();
            hidden.countDown();
        });
        hidden.await(5, TimeUnit.SECONDS);
    }
}
