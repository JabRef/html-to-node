package org.jabref.htmltonode.rich;

import java.util.Optional;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.layout.StackPane;

import org.jabref.htmltonode.HtmlRenderOptions;
import org.jabref.htmltonode.HtmlToNode;

import jfx.incubator.scene.control.richtext.RichTextArea;
import jfx.incubator.scene.control.richtext.SelectionSegment;
import jfx.incubator.scene.control.richtext.TextPos;
import jfx.incubator.scene.control.richtext.model.StyledTextModel;
import org.jspecify.annotations.Nullable;

/// A pane rendering an HTML string into a read-only [RichTextArea] — like
/// `org.jabref.htmltonode.HtmlView`, but with native text selection, caret navigation, and
/// accessibility. The area scrolls itself; do not wrap this view in a `ScrollPane`.
///
/// Re-renders whenever [#htmlProperty()] or [#optionsProperty()] changes. Must be used on the
/// JavaFX application thread. Requires the `jfx.incubator.richtext` module at runtime.
public class RichHtmlView extends StackPane {

    private final RichTextArea area = new HtmlRichTextArea();
    private final StringProperty html = new SimpleStringProperty(this, "html", "");
    private final ObjectProperty<HtmlRenderOptions> options =
            new SimpleObjectProperty<>(this, "options", HtmlRenderOptions.defaults());

    /// Creates an empty view with [HtmlRenderOptions#defaults()].
    public RichHtmlView() {
        setAlignment(Pos.TOP_LEFT);
        getStylesheets().add(HtmlToNode.stylesheet());
        getChildren().add(area);
        html.addListener((observable, oldValue, newValue) -> rerender());
        options.addListener((observable, oldValue, newValue) -> rerender());
        rerender();
    }

    /// The HTML content of this view. Setting a new value re-renders. Never `null`.
    ///
    /// @return the html property
    public final StringProperty htmlProperty() {
        return html;
    }

    /// @return the current HTML content; never `null`
    public final String getHtml() {
        return html.get();
    }

    /// @param newHtml the HTML content to render; `null` is treated as empty
    public final void setHtml(@Nullable String newHtml) {
        html.set(newHtml == null ? "" : newHtml);
    }

    /// The rendering options of this view. Setting a new value re-renders. Never `null`.
    ///
    /// @return the options property
    public final ObjectProperty<HtmlRenderOptions> optionsProperty() {
        return options;
    }

    /// @return the current rendering options; never `null`
    public final HtmlRenderOptions getOptions() {
        return options.get();
    }

    /// @param newOptions the rendering options; `null` restores [HtmlRenderOptions#defaults()]
    public final void setOptions(@Nullable HtmlRenderOptions newOptions) {
        options.set(newOptions == null ? HtmlRenderOptions.defaults() : newOptions);
    }

    /// @return the backing control, e.g. for copy actions, printing, or selection access
    public final RichTextArea getRichTextArea() {
        return area;
    }

    /// Sizes the area to its content so that an enclosing pane handles the scrolling —
    /// by default the area scrolls itself.
    ///
    /// @param on whether the area should size to its content
    public final void setUseContentHeight(boolean on) {
        area.setUseContentHeight(on);
    }

    /// @return the selected text, joined with line breaks at paragraph boundaries; empty if
    /// nothing is selected
    public final Optional<String> getSelectedText() {
        SelectionSegment selection = area.getSelection();
        if ((selection == null) || selection.isCollapsed()) {
            return Optional.empty();
        }
        StyledTextModel model = area.getModel();
        TextPos min = selection.getMin();
        TextPos max = selection.getMax();
        StringBuilder result = new StringBuilder();
        for (int i = min.index(); i <= max.index(); i++) {
            String paragraph = model.getPlainText(i);
            int from = (i == min.index()) ? Math.min(min.offset(), paragraph.length()) : 0;
            int to = (i == max.index()) ? Math.min(max.offset(), paragraph.length()) : paragraph.length();
            if (i > min.index()) {
                result.append('\n');
            }
            result.append(paragraph, from, Math.max(from, to));
        }
        return result.isEmpty() ? Optional.empty() : Optional.of(result.toString());
    }

    /// @param screenX the horizontal screen coordinate of a mouse press
    /// @param screenY the vertical screen coordinate of a mouse press
    /// @return whether the given position lies on the current text selection — only then a
    /// drag gesture should drag the selected content instead of extending the selection
    public final boolean isPressOnSelection(double screenX, double screenY) {
        SelectionSegment selection = area.getSelection();
        if ((selection == null) || selection.isCollapsed()) {
            return false;
        }
        TextPos position = area.getTextPosition(screenX, screenY);
        return (position != null)
                && (selection.getMin().compareTo(position) <= 0)
                && (position.compareTo(selection.getMax()) <= 0);
    }

    /// Extracts readable plain text from the current content, e.g. for "copy as text".
    ///
    /// @return the plain text; see [HtmlToNode#toPlainText(String)]
    public final String toPlainText() {
        return HtmlToNode.toPlainText(getHtml());
    }

    private void rerender() {
        HtmlRenderOptions renderOptions = getOptions();
        area.setModel(RichTextRenderer.buildModel(HtmlToNode.parse(getHtml(), renderOptions.baseUri()), renderOptions));
        RichTextRenderer.configure(area, renderOptions);
    }
}
