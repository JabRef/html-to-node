package org.jabref.htmltonode;

import javafx.geometry.Bounds;
import javafx.scene.layout.VBox;
import javafx.scene.shape.PathElement;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies the core assumption of this library's test setup: creating and laying out
/// Text/TextFlow works without a display and without starting the JavaFX (glass) toolkit,
/// because only prism font loading is involved. If this test fails on a machine, all
/// rendering tests need a real display (e.g. xvfb-run).
class HeadlessFxSmokeTest {

    @Test
    void textFlowLaysOutWithoutToolkit() {
        Font bold = Font.font("System", FontWeight.BOLD, 14);
        Font italic = Font.font("System", FontWeight.NORMAL, FontPosture.ITALIC, 14);
        assertNotNull(bold);

        Text first = new Text("Hello ");
        first.setFont(bold);
        Text second = new Text("world, this is a longer run that should wrap at some point.");
        second.setFont(italic);

        TextFlow flow = new TextFlow(first, second);
        flow.resize(120, flow.prefHeight(120));
        flow.layout();

        Bounds bounds = flow.getLayoutBounds();
        assertTrue(bounds.getHeight() > 0, "flow should have computed a text layout height");

        PathElement[] range = flow.rangeShape(0, 5);
        assertNotNull(range);
        assertTrue(range.length > 0, "rangeShape should produce path elements");

        VBox box = new VBox(flow);
        box.resize(120, 200);
        box.layout();
        assertNotEquals(0, box.getChildren().size());
    }
}
