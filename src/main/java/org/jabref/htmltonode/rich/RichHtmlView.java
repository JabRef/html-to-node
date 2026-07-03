package org.jabref.htmltonode.rich;

import javafx.beans.property.ObjectProperty;
import javafx.geometry.Pos;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.layout.StackPane;

import org.jabref.htmltonode.HtmlRenderOptions;
import org.jabref.htmltonode.HtmlToNode;

import jfx.incubator.scene.control.richtext.RichTextArea;
import org.jspecify.annotations.Nullable;

/// A pane rendering an HTML string into a read-only [RichTextArea] — like
/// `org.jabref.htmltonode.HtmlView`, but with native text selection, caret navigation, and
/// accessibility. The area scrolls itself; do not wrap this view in a `ScrollPane`.
///
/// Re-renders whenever [#htmlProperty()] or [#optionsProperty()] changes. Must be used on the
/// JavaFX application thread. Requires the `jfx.incubator.richtext` module at runtime.
public class RichHtmlView extends StackPane {

    private final RichTextArea area = new RichTextArea();
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
