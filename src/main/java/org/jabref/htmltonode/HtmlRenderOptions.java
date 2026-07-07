package org.jabref.htmltonode;

import java.util.function.Consumer;

import javafx.scene.text.Font;

import org.jspecify.annotations.Nullable;

/// Rendering options. Immutable; start from [#defaults()] and refine via `with…` methods:
///
/// ```java
/// HtmlRenderOptions options = HtmlRenderOptions.defaults()
///         .withLinkHandler(url -> openInBrowser(url))
///         .withBaseUri(libraryFileDirectory.toUri().toString());
/// ```
public final class HtmlRenderOptions {

    private final double baseFontSize;
    private final @Nullable String baseFontFamily;
    private final String monospaceFontFamily;
    private final Consumer<String> linkHandler;
    private final @Nullable String baseUri;
    private final boolean renderImages;
    private final boolean loadRemoteImages;
    private final boolean renderMath;

    private HtmlRenderOptions(double baseFontSize,
                              @Nullable String baseFontFamily,
                              String monospaceFontFamily,
                              Consumer<String> linkHandler,
                              @Nullable String baseUri,
                              boolean renderImages,
                              boolean loadRemoteImages,
                              boolean renderMath) {
        this.baseFontSize = baseFontSize;
        this.baseFontFamily = baseFontFamily;
        this.monospaceFontFamily = monospaceFontFamily;
        this.linkHandler = linkHandler;
        this.baseUri = baseUri;
        this.renderImages = renderImages;
        this.loadRemoteImages = loadRemoteImages;
        this.renderMath = renderMath;
    }

    /// Default options: system font and size, links ignored, `file:`/`data:` images rendered,
    /// remote images not loaded, no base URI, no math rendering.
    ///
    /// @return the default options
    public static HtmlRenderOptions defaults() {
        return new HtmlRenderOptions(-1, null, "Monospaced", url -> {
        }, null, true, false, false);
    }

    /// Base font size in points/pixels; unset means [Font#getDefault()]'s size.
    public HtmlRenderOptions withBaseFontSize(double newBaseFontSize) {
        return new HtmlRenderOptions(newBaseFontSize, baseFontFamily, monospaceFontFamily, linkHandler, baseUri, renderImages, loadRemoteImages, renderMath);
    }

    /// Font family for regular text; unset means [Font#getDefault()]'s family.
    public HtmlRenderOptions withBaseFontFamily(@Nullable String newBaseFontFamily) {
        return new HtmlRenderOptions(baseFontSize, newBaseFontFamily, monospaceFontFamily, linkHandler, baseUri, renderImages, loadRemoteImages, renderMath);
    }

    /// Font family for `code`/`pre` content. Default: `"Monospaced"` (JavaFX logical font).
    public HtmlRenderOptions withMonospaceFontFamily(String newMonospaceFontFamily) {
        return new HtmlRenderOptions(baseFontSize, baseFontFamily, newMonospaceFontFamily, linkHandler, baseUri, renderImages, loadRemoteImages, renderMath);
    }

    /// Invoked with the (resolved) `href` when a link is clicked. Default: no-op.
    public HtmlRenderOptions withLinkHandler(Consumer<String> newLinkHandler) {
        return new HtmlRenderOptions(baseFontSize, baseFontFamily, monospaceFontFamily, newLinkHandler, baseUri, renderImages, loadRemoteImages, renderMath);
    }

    /// Base URI against which relative `href`/`src` values are resolved
    /// (replacement for the `<base href>` JabRef injects for WebView today).
    ///
    /// @param newBaseUri the base URI; use [#withoutBaseUri()] to express absence
    public HtmlRenderOptions withBaseUri(String newBaseUri) {
        return new HtmlRenderOptions(baseFontSize, baseFontFamily, monospaceFontFamily, linkHandler, newBaseUri, renderImages, loadRemoteImages, renderMath);
    }

    /// Leaves relative `href`/`src` values unresolved (the default).
    public HtmlRenderOptions withoutBaseUri() {
        return new HtmlRenderOptions(baseFontSize, baseFontFamily, monospaceFontFamily, linkHandler, null, renderImages, loadRemoteImages, renderMath);
    }

    /// Disable to skip `<img>` entirely (useful in tests and tooltips).
    public HtmlRenderOptions withRenderImages(boolean newRenderImages) {
        return new HtmlRenderOptions(baseFontSize, baseFontFamily, monospaceFontFamily, linkHandler, baseUri, newRenderImages, loadRemoteImages, renderMath);
    }

    /// Allow `http(s):` image sources. Off by default; `file:`/`data:`/`jar:` always load.
    public HtmlRenderOptions withLoadRemoteImages(boolean newLoadRemoteImages) {
        return new HtmlRenderOptions(baseFontSize, baseFontFamily, monospaceFontFamily, linkHandler, baseUri, renderImages, newLoadRemoteImages, renderMath);
    }

    /// Enable to recognize TeX math in text content (`$…$`, `$$…$$`, `\(…\)`, `\[…\]`)
    /// and render it as equations (via JLaTeXMath). Off by default: a `$` is ordinary text in
    /// most HTML, and recognition applies delimiter heuristics that only make sense for content
    /// that is known to contain TeX (like BibTeX-sourced previews). Code (`<code>`/`<pre>`) and
    /// link text are never scanned.
    public HtmlRenderOptions withRenderMath(boolean newRenderMath) {
        return new HtmlRenderOptions(baseFontSize, baseFontFamily, monospaceFontFamily, linkHandler, baseUri, renderImages, loadRemoteImages, newRenderMath);
    }

    /// @return the configured base font size, or a non-positive value if unset
    public double baseFontSize() {
        return baseFontSize;
    }

    /// @return the configured base font family, or `null` if unset
    public @Nullable String baseFontFamily() {
        return baseFontFamily;
    }

    /// @return the font family used for monospace runs
    public String monospaceFontFamily() {
        return monospaceFontFamily;
    }

    /// @return the handler invoked with the resolved `href` of a clicked link
    public Consumer<String> linkHandler() {
        return linkHandler;
    }

    /// @return the base URI for resolving relative URLs, or `null` if unset
    public @Nullable String baseUri() {
        return baseUri;
    }

    /// @return whether `<img>` elements are rendered at all
    public boolean renderImages() {
        return renderImages;
    }

    /// @return whether `http(s):` image sources may be loaded
    public boolean loadRemoteImages() {
        return loadRemoteImages;
    }

    /// @return whether TeX math in text content is recognized and rendered as equations
    public boolean renderMath() {
        return renderMath;
    }

    /// @return the effective base font size: the configured one, or [Font#getDefault()]'s size
    public double resolvedBaseFontSize() {
        return baseFontSize > 0 ? baseFontSize : Font.getDefault().getSize();
    }

    /// @return the effective base font family: the configured one, or [Font#getDefault()]'s family
    public String resolvedBaseFontFamily() {
        return baseFontFamily != null ? baseFontFamily : Font.getDefault().getFamily();
    }
}
