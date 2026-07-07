package org.jabref.htmltonode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// End-to-end rendering of math spans; needs the JavaFX toolkit (JLaTeXMath rasterizes into a
/// [javafx.scene.image.WritableImage]). Run via `./gradlew guiTest` with a display or `xvfb-run`.
@Tag("gui")
class MathRenderingTest {

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
    void validMathRendersAsAnEquationNode() throws Exception {
        Region root = renderOnFxThread("<p>mass $E=mc^2$ energy</p>");
        assertTrue(hasMathNode(root), "expected an html-math node for $E=mc^2$");
    }

    @Test
    void unparseableMathFallsBackToSourceText() throws Exception {
        Region root = renderOnFxThread("<p>see $\\nosuchcommand$ here</p>");
        assertFalse(hasMathNode(root), "broken TeX should not produce a math node");
        assertTrue(collectText(root).contains("$\\nosuchcommand$"),
                "broken TeX should fall back to its verbatim source");
    }

    private static Region renderOnFxThread(String html) throws Exception {
        AtomicReference<Region> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                Region root = HtmlToNode.render(html, HtmlRenderOptions.defaults().withRenderMath(true));
                new Scene(root, 400, 200);
                root.applyCss();
                root.layout();
                result.set(root);
            } catch (Throwable t) {
                error.set(t);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(15, TimeUnit.SECONDS), "FX task timed out");
        if (error.get() != null) {
            throw new AssertionError("FX render failed", error.get());
        }
        return result.get();
    }

    private static boolean hasMathNode(Node node) {
        if (node.getStyleClass().contains("html-math")) {
            return true;
        }
        return node instanceof Parent parent
                && parent.getChildrenUnmodifiable().stream().anyMatch(MathRenderingTest::hasMathNode);
    }

    private static String collectText(Node node) {
        List<String> parts = new ArrayList<>();
        collectText(node, parts);
        return String.join("", parts);
    }

    private static void collectText(Node node, List<String> parts) {
        if (node instanceof Text text) {
            parts.add(text.getText());
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collectText(child, parts));
        }
    }
}
