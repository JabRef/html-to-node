package org.jabref.htmltonode.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.text.HitInfo;

import org.jspecify.annotations.Nullable;

/// Mouse-driven text selection across all [HighlightTextFlow]s of a rendered document.
///
/// A drag selects from the anchor to the current position in document order (the order in
/// which the flows appear in the scene graph). Each affected flow paints its part of the
/// selection; the selected string joins the per-flow parts with line breaks.
public final class TextSelection {

    private record FlowPosition(int flowIndex, int charIndex) {
    }

    private final Region root;
    private final List<HighlightTextFlow> flows = new ArrayList<>();

    private @Nullable FlowPosition anchor;
    private @Nullable FlowPosition extent;
    private boolean pressOnSelection;

    public TextSelection(Region root) {
        this.root = root;
        root.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }
            collectFlows();
            FlowPosition position = positionAt(event.getX(), event.getY());
            if (isInsideSelection(position)) {
                // like in a browser: a press on the selection may start dragging it,
                // so the selection survives until the gesture is resolved on release
                pressOnSelection = true;
                return;
            }
            pressOnSelection = false;
            clear();
            anchor = position;
        });
        root.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if ((anchor != null) && !pressOnSelection && event.getButton() == MouseButton.PRIMARY) {
                extent = positionAt(event.getX(), event.getY());
                applySelection();
            }
        });
        root.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (pressOnSelection && event.isStillSincePress()) {
                // a plain click on the selection deselects
                clear();
            }
            pressOnSelection = false;
        });
    }

    /// @return whether the current mouse press started on an existing selection
    /// (the consumer may start a drag-and-drop of the selected content then)
    public boolean isPressOnSelection() {
        return pressOnSelection;
    }

    private boolean isInsideSelection(@Nullable FlowPosition position) {
        if ((position == null) || (anchor == null) || (extent == null) || getSelectedText().isEmpty()) {
            return false;
        }
        return (compare(min(anchor, extent), position) <= 0)
                && (compare(position, max(anchor, extent)) <= 0);
    }

    /// @return the selected text, joined with line breaks at block boundaries; empty if nothing is selected
    public Optional<String> getSelectedText() {
        if ((anchor == null) || (extent == null)) {
            return Optional.empty();
        }
        FlowPosition from = min(anchor, extent);
        FlowPosition to = max(anchor, extent);
        StringBuilder result = new StringBuilder();
        for (int i = from.flowIndex(); i <= to.flowIndex(); i++) {
            HighlightTextFlow flow = flows.get(i);
            int start = (i == from.flowIndex()) ? from.charIndex() : 0;
            int end = (i == to.flowIndex()) ? to.charIndex() : flow.charLength();
            if (i > from.flowIndex()) {
                result.append('\n');
            }
            result.append(flow.textInRange(start, end));
        }
        return result.isEmpty() ? Optional.empty() : Optional.of(result.toString());
    }

    public void clear() {
        anchor = null;
        extent = null;
        flows.forEach(HighlightTextFlow::clearSelectionRange);
    }

    private void applySelection() {
        FlowPosition from = min(anchor, extent);
        FlowPosition to = max(anchor, extent);
        for (int i = 0; i < flows.size(); i++) {
            HighlightTextFlow flow = flows.get(i);
            if ((i < from.flowIndex()) || (i > to.flowIndex())) {
                flow.clearSelectionRange();
            } else {
                int start = (i == from.flowIndex()) ? from.charIndex() : 0;
                int end = (i == to.flowIndex()) ? to.charIndex() : flow.charLength();
                flow.setSelectionRange(start, end);
            }
        }
    }

    private void collectFlows() {
        flows.clear();
        collectFlows(root);
    }

    private void collectFlows(Parent parent) {
        for (Node child : parent.getChildrenUnmodifiable()) {
            if (child instanceof HighlightTextFlow flow) {
                flows.add(flow);
            } else if (child instanceof Parent childParent) {
                collectFlows(childParent);
            }
        }
    }

    /// Maps a point (in root coordinates) to the nearest flow and character index.
    private @Nullable FlowPosition positionAt(double x, double y) {
        if (flows.isEmpty()) {
            return null;
        }
        int bestIndex = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < flows.size(); i++) {
            Bounds bounds = boundsInRoot(flows.get(i));
            double dy = distanceOutside(y, bounds.getMinY(), bounds.getMaxY());
            double dx = distanceOutside(x, bounds.getMinX(), bounds.getMaxX());
            // vertical distance dominates: pick the flow of the pointed-at line first
            double distance = (dy * 100_000) + dx;
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        HighlightTextFlow flow = flows.get(bestIndex);
        Point2D inFlow = flow.sceneToLocal(root.localToScene(x, y));
        HitInfo hit = flow.hitTest(inFlow);
        return new FlowPosition(bestIndex, hit.getInsertionIndex());
    }

    private Bounds boundsInRoot(HighlightTextFlow flow) {
        return root.sceneToLocal(flow.localToScene(flow.getBoundsInLocal()));
    }

    private static double distanceOutside(double value, double min, double max) {
        if (value < min) {
            return min - value;
        }
        if (value > max) {
            return value - max;
        }
        return 0;
    }

    private static FlowPosition min(FlowPosition a, FlowPosition b) {
        return compare(a, b) <= 0 ? a : b;
    }

    private static FlowPosition max(FlowPosition a, FlowPosition b) {
        return compare(a, b) <= 0 ? b : a;
    }

    private static int compare(FlowPosition a, FlowPosition b) {
        int byFlow = Integer.compare(a.flowIndex(), b.flowIndex());
        return byFlow != 0 ? byFlow : Integer.compare(a.charIndex(), b.charIndex());
    }
}
