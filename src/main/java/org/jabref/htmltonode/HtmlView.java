package org.jabref.htmltonode;

import java.util.Optional;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.layout.VBox;

import org.jabref.htmltonode.internal.TextSelection;
import org.jspecify.annotations.Nullable;

/// A small pane that renders an HTML string as JavaFX nodes — the drop-in content for the
/// `ScrollPane` that JabRef's `PreviewViewer` already is (instead of a `WebView`).
///
/// Not a live browser: re-renders whenever [#htmlProperty()] or [#optionsProperty()] changes.
/// Must be used on the JavaFX application thread, like any other node.
public class HtmlView extends VBox {

    private final StringProperty html = new SimpleStringProperty(this, "html", "");
    private final ObjectProperty<HtmlRenderOptions> options =
            new SimpleObjectProperty<>(this, "options", HtmlRenderOptions.defaults());

    /// Creates an empty view with [HtmlRenderOptions#defaults()].
    public HtmlView() {
        setFillWidth(true);
        html.addListener((observable, oldValue, newValue) -> rerender());
        options.addListener((observable, oldValue, newValue) -> rerender());
    }

    /// Creates a view showing `html` with [HtmlRenderOptions#defaults()].
    ///
    /// @param html the initial HTML content; `null` is treated as empty
    public HtmlView(@Nullable String html) {
        this();
        setHtml(html);
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

    /// Extracts readable plain text from the current content, e.g. for "copy as text".
    ///
    /// @return the plain text; see [HtmlToNode#toPlainText(String)]
    public final String toPlainText() {
        return HtmlToNode.toPlainText(getHtml());
    }

    /// @return the text currently selected with the mouse, if any
    public final Optional<String> getSelectedText() {
        return selection().flatMap(TextSelection::getSelectedText);
    }

    /// Clears the mouse text selection.
    public final void clearSelection() {
        selection().ifPresent(TextSelection::clear);
    }

    /// @return whether the current mouse press started on an existing selection —
    /// drag-and-drop consumers should only drag the content in that case, otherwise
    /// dragging extends the text selection
    public final boolean isPressOnSelection() {
        return selection().map(TextSelection::isPressOnSelection).orElse(false);
    }

    private Optional<TextSelection> selection() {
        return getChildren().isEmpty()
                ? Optional.empty()
                : Optional.ofNullable((TextSelection) getChildren().getFirst().getProperties().get(TextSelection.class));
    }

    private void rerender() {
        getChildren().setAll(HtmlToNode.render(getHtml(), getOptions()));
    }
}
