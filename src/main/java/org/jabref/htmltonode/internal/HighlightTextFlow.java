package org.jabref.htmltonode.internal;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/// A [TextFlow] that can paint real background rectangles behind character ranges —
/// used for `<mark>`/`background-color` runs (JabRef's search-hit highlighting) and
/// for the text selection driven by [TextSelection].
///
/// The backgrounds live in unmanaged, mouse-transparent [Group]s that are the first children,
/// so they render behind the text and are ignored by the flow's text layout. Shapes are
/// recomputed from [TextFlow#rangeShape] after every layout pass.
///
/// Character indices are relative to the flow's text layout: each [Text] child contributes
/// its text length, every other managed child (e.g. an embedded image) counts as one character.
public final class HighlightTextFlow extends TextFlow {

    /// Fallback selection color; the stylesheet overrides it via `.html-selection`
    private static final Paint SELECTION_FILL = Color.rgb(0x33, 0x99, 0xFF, 0.4);

    private record HighlightRange(int start, int end, Paint fill) {
    }

    private final List<HighlightRange> ranges = new ArrayList<>();
    private final Group highlightLayer = new Group();
    private final Group selectionLayer = new Group();

    private int selectionStart = -1;
    private int selectionEnd = -1;

    public HighlightTextFlow() {
        highlightLayer.setManaged(false);
        highlightLayer.setMouseTransparent(true);
        selectionLayer.setManaged(false);
        selectionLayer.setMouseTransparent(true);
        // selection paints over highlights, both stay behind the text
        getChildren().addAll(highlightLayer, selectionLayer);
        setCursor(Cursor.TEXT);
    }

    public void addHighlight(int start, int end, Paint fill) {
        if (end > start) {
            ranges.add(new HighlightRange(start, end, fill));
            requestLayout();
        }
    }

    /// Marks `[start, end)` as selected (replacing any previous selection of this flow).
    public void setSelectionRange(int start, int end) {
        if ((start == selectionStart) && (end == selectionEnd)) {
            return;
        }
        selectionStart = start;
        selectionEnd = end;
        requestLayout();
    }

    public void clearSelectionRange() {
        setSelectionRange(-1, -1);
    }

    /// @return the total character length of this flow's text layout
    public int charLength() {
        int length = 0;
        for (Node child : getChildren()) {
            if (child instanceof Text text) {
                length += text.getText().length();
            } else if (child.isManaged()) {
                length += 1;
            }
        }
        return length;
    }

    /// Extracts the text of `[start, end)`; non-text children (images) contribute nothing.
    public String textInRange(int start, int end) {
        StringBuilder result = new StringBuilder();
        int index = 0;
        for (Node child : getChildren()) {
            if (child instanceof Text text) {
                String content = text.getText();
                int childStart = Math.max(start - index, 0);
                int childEnd = Math.min(end - index, content.length());
                if (childStart < childEnd) {
                    result.append(content, childStart, childEnd);
                }
                index += content.length();
            } else if (child.isManaged()) {
                index += 1;
            }
        }
        return result.toString();
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        layoutRangeLayer(highlightLayer, ranges);
        if ((selectionStart >= 0) && (selectionEnd > selectionStart)) {
            layoutRangeLayer(selectionLayer,
                    List.of(new HighlightRange(selectionStart, selectionEnd, SELECTION_FILL)));
        } else {
            selectionLayer.getChildren().clear();
        }
    }

    private void layoutRangeLayer(Group layer, List<HighlightRange> layerRanges) {
        if (layerRanges.isEmpty()) {
            return;
        }
        List<Path> paths = new ArrayList<>(layerRanges.size());
        for (HighlightRange range : layerRanges) {
            PathElement[] shape = rangeShape(range.start(), range.end());
            if (shape.length == 0) {
                continue;
            }
            Path path = new Path(shape);
            path.setFill(range.fill());
            path.setStroke(null);
            path.setManaged(false);
            path.getStyleClass().add(layer == selectionLayer ? "html-selection" : "html-mark");
            paths.add(path);
        }
        layer.getChildren().setAll(paths);
    }
}
