package org.jabref.htmltonode.internal;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.Group;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.text.TextFlow;

/// A [TextFlow] that can paint real background rectangles behind character ranges —
/// used for `<mark>`/`background-color` runs (JabRef's search-hit highlighting).
///
/// The backgrounds live in an unmanaged, mouse-transparent [Group] that is the first child,
/// so it renders behind the text and is ignored by the flow's text layout. Shapes are
/// recomputed from [TextFlow#rangeShape] after every layout pass.
///
/// Character indices are relative to the flow's text layout: each [javafx.scene.text.Text]
/// child contributes its text length, every other managed child (e.g. an embedded image)
/// counts as one character.
public final class HighlightTextFlow extends TextFlow {

    private record HighlightRange(int start, int end, Paint fill) {
    }

    private final List<HighlightRange> ranges = new ArrayList<>();
    private final Group highlightLayer = new Group();

    public HighlightTextFlow() {
        highlightLayer.setManaged(false);
        highlightLayer.setMouseTransparent(true);
        getChildren().add(highlightLayer);
    }

    public void addHighlight(int start, int end, Paint fill) {
        if (end > start) {
            ranges.add(new HighlightRange(start, end, fill));
            requestLayout();
        }
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        if (ranges.isEmpty()) {
            return;
        }
        List<Path> paths = new ArrayList<>(ranges.size());
        for (HighlightRange range : ranges) {
            PathElement[] shape = rangeShape(range.start(), range.end());
            if (shape.length == 0) {
                continue;
            }
            Path path = new Path(shape);
            path.setFill(range.fill());
            path.setStroke(null);
            path.setManaged(false);
            path.getStyleClass().add("html-mark");
            paths.add(path);
        }
        highlightLayer.getChildren().setAll(paths);
    }
}
