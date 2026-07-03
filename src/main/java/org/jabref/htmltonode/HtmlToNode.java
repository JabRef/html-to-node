package org.jabref.htmltonode;

import java.util.List;
import java.util.Objects;

import javafx.scene.layout.Region;

import org.jabref.htmltonode.internal.HtmlToModel;
import org.jabref.htmltonode.internal.PlainText;
import org.jabref.htmltonode.model.Block;
import org.jspecify.annotations.Nullable;

/// Entry point: converts HTML (the subset produced for JabRef's entry preview — citeproc-java
/// CSL output, JabRef preview layouts, flexmark markdown, `<mark>` search highlights) into
/// plain JavaFX nodes, with no dependency on `javafx.web`.
///
/// ```java
/// Region preview = HtmlToNode.render(citationHtml, HtmlRenderOptions.defaults()
///         .withLinkHandler(NativeDesktop::openBrowserShowPopup));
/// scrollPane.setContent(preview);
/// ```
public final class HtmlToNode {

    private HtmlToNode() {
    }

    /// Parses HTML into the renderer-independent block model.
    ///
    /// @param html the HTML to parse; documents, fragments and malformed markup are all accepted
    /// @return the parsed blocks, in document order; empty for blank input, never `null`
    public static List<Block> parse(String html) {
        return HtmlToModel.parse(html, null);
    }

    /// Parses HTML, resolving relative links and image sources against `baseUri`
    /// (a `<base href>` inside the HTML wins, as in a browser).
    ///
    /// @param html    the HTML to parse
    /// @param baseUri the URI to resolve relative `href`/`src` values against, or `null` for none
    /// @return the parsed blocks, in document order; empty for blank input, never `null`
    public static List<Block> parse(String html, @Nullable String baseUri) {
        return HtmlToModel.parse(html, baseUri);
    }

    /// Parses and renders HTML with [HtmlRenderOptions#defaults()].
    ///
    /// @param html the HTML to render
    /// @return the rendered content, ready to be placed in a `ScrollPane` or any other parent
    public static Region render(String html) {
        return render(html, HtmlRenderOptions.defaults());
    }

    /// Parses and renders HTML in one step, resolving relative URLs against
    /// [HtmlRenderOptions#baseUri()].
    ///
    /// @param html    the HTML to render
    /// @param options rendering options
    /// @return the rendered content, ready to be placed in a `ScrollPane` or any other parent
    public static Region render(String html, HtmlRenderOptions options) {
        return FxRenderer.render(parse(html, options.baseUri()), options);
    }

    /// Renders an already parsed block model, e.g. to render the result of [#parse(String)]
    /// several times or with different options.
    ///
    /// @param blocks  the blocks to render
    /// @param options rendering options
    /// @return the rendered content, ready to be placed in a `ScrollPane` or any other parent
    public static Region render(List<Block> blocks, HtmlRenderOptions options) {
        return FxRenderer.render(blocks, options);
    }

    /// Extracts readable plain text, comparable to the DOM's `document.body.innerText`
    /// (which JabRef's "Copy preview" uses with WebView today).
    ///
    /// @param html the HTML to convert
    /// @return the text content with paragraph breaks, list markers and tab-separated table cells
    public static String toPlainText(String html) {
        return PlainText.of(parse(html, null));
    }

    /// Extracts readable plain text from an already parsed block model.
    ///
    /// @param blocks the blocks to convert
    /// @return the text content with paragraph breaks, list markers and tab-separated table cells
    public static String toPlainText(List<Block> blocks) {
        return PlainText.of(blocks);
    }

    /// URL of the built-in stylesheet (already attached to rendered roots). Add it to a scene
    /// only when styling nodes that were re-parented out of the rendered tree.
    ///
    /// @return the stylesheet URL in the form accepted by `Parent#getStylesheets()`
    public static String stylesheet() {
        return Objects.requireNonNull(HtmlToNode.class.getResource("html-to-node.css"),
                "html-to-node.css missing from resources").toExternalForm();
    }
}
