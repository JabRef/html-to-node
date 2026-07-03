package org.jabref.htmltonode;

import java.util.Optional;

import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.Group;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextFlow;

import org.jabref.htmltonode.internal.TextSelection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Headless: selection works via hit testing and range shapes, no Scene or toolkit required.
class TextSelectionTest {

    private static final HtmlRenderOptions OPTIONS = HtmlRenderOptions.defaults()
            .withBaseFontSize(12)
            .withBaseFontFamily("System");

    private static VBox renderAndLayout(String html) {
        VBox root = (VBox) HtmlToNode.render(html, OPTIONS);
        root.resize(400, root.prefHeight(400));
        root.layout();
        return root;
    }

    private static TextSelection selectionOf(VBox root) {
        return (TextSelection) root.getProperties().get(TextSelection.class);
    }

    private static double centerY(VBox root, int childIndex) {
        return root.getChildren().get(childIndex).getBoundsInParent().getCenterY();
    }

    private static void fire(VBox root, EventType<MouseEvent> type, double x, double y) {
        fire(root, type, x, y, false);
    }

    private static void fire(VBox root, EventType<MouseEvent> type, double x, double y, boolean stillSincePress) {
        Event.fireEvent(root, new MouseEvent(type, x, y, 0, 0, MouseButton.PRIMARY, 1,
                false, false, false, false,
                true, false, false,
                false, false, stillSincePress, null));
    }

    private static void drag(VBox root, double x1, double y1, double x2, double y2) {
        fire(root, MouseEvent.MOUSE_PRESSED, x1, y1);
        fire(root, MouseEvent.MOUSE_DRAGGED, x2, y2);
        fire(root, MouseEvent.MOUSE_RELEASED, x2, y2);
    }

    @Test
    void dragWithinOneParagraphSelectsItsText() {
        VBox root = renderAndLayout("<p>First entry</p><p>Second one</p>");
        drag(root, 1, centerY(root, 0), 399, centerY(root, 0));

        assertEquals(Optional.of("First entry"), selectionOf(root).getSelectedText());
    }

    @Test
    void dragAcrossParagraphsJoinsWithLineBreak() {
        VBox root = renderAndLayout("<p>First entry</p><p>Second one</p>");
        drag(root, 1, centerY(root, 0), 399, centerY(root, 1));

        assertEquals(Optional.of("First entry\nSecond one"), selectionOf(root).getSelectedText());
    }

    @Test
    void reverseDragSelectsSameRange() {
        VBox root = renderAndLayout("<p>First entry</p><p>Second one</p>");
        drag(root, 399, centerY(root, 1), 1, centerY(root, 0));

        assertEquals(Optional.of("First entry\nSecond one"), selectionOf(root).getSelectedText());
    }

    @Test
    void selectionKeepsStyledRunsTogether() {
        VBox root = renderAndLayout("<p>plain <b>bold</b> tail</p>");
        drag(root, 1, centerY(root, 0), 399, centerY(root, 0));

        assertEquals(Optional.of("plain bold tail"), selectionOf(root).getSelectedText());
    }

    @Test
    void pressClearsPreviousSelection() {
        VBox root = renderAndLayout("<p>First entry</p><p>Second one</p>");
        drag(root, 1, centerY(root, 0), 399, centerY(root, 0));
        fire(root, MouseEvent.MOUSE_PRESSED, 1, centerY(root, 1));

        assertEquals(Optional.empty(), selectionOf(root).getSelectedText());
    }

    @Test
    void selectionPaintsRangeShapes() {
        VBox root = renderAndLayout("<p>First entry</p>");
        drag(root, 1, centerY(root, 0), 399, centerY(root, 0));
        root.layout();

        TextFlow flow = (TextFlow) root.getChildren().getFirst();
        Group selectionLayer = (Group) flow.getChildrenUnmodifiable().get(1);
        assertFalse(selectionLayer.getChildren().isEmpty(), "selection layer should contain shapes");
        assertTrue(selectionLayer.getChildren().getFirst().getStyleClass().contains("html-selection"));
    }

    @Test
    void pressOnSelectionKeepsItForDragAndDrop() {
        VBox root = renderAndLayout("<p>First entry</p><p>Second one</p>");
        drag(root, 1, centerY(root, 0), 399, centerY(root, 0));

        fire(root, MouseEvent.MOUSE_PRESSED, 200, centerY(root, 0));

        assertTrue(selectionOf(root).isPressOnSelection());
        assertEquals(Optional.of("First entry"), selectionOf(root).getSelectedText());
    }

    @Test
    void clickOnSelectionClearsIt() {
        VBox root = renderAndLayout("<p>First entry</p><p>Second one</p>");
        drag(root, 1, centerY(root, 0), 399, centerY(root, 0));

        fire(root, MouseEvent.MOUSE_PRESSED, 200, centerY(root, 0));
        fire(root, MouseEvent.MOUSE_RELEASED, 200, centerY(root, 0), true);

        assertEquals(Optional.empty(), selectionOf(root).getSelectedText());
        assertFalse(selectionOf(root).isPressOnSelection());
    }

    @Test
    void clearRemovesSelection() {
        VBox root = renderAndLayout("<p>First entry</p>");
        drag(root, 1, centerY(root, 0), 399, centerY(root, 0));
        selectionOf(root).clear();

        assertEquals(Optional.empty(), selectionOf(root).getSelectedText());
    }
}
