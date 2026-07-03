package org.jabref.htmltonode;

import java.util.Objects;
import java.util.function.Consumer;

import javafx.scene.text.Font;

/// Rendering options. Immutable; start from [#defaults()] and refine via `with…` methods:
///
/// ```java
/// HtmlRenderOptions options = HtmlRenderOptions.defaults()
///         .withLinkHandler(url -> openInBrowser(url))
///         .withBaseUri(libraryFileDirectory.toUri().toString());
/// ```
public final class HtmlRenderOptions {

    private final double baseFontSize;
    private final String baseFontFamily;
    private final String monospaceFontFamily;
    private final Consumer<String> linkHandler;
    private final String baseUri;
    private final boolean renderImages;
    private final boolean loadRemoteImages;

    private HtmlRenderOptions(double baseFontSize,
                              String baseFontFamily,
                              String monospaceFontFamily,
                              Consumer<String> linkHandler,
                              String baseUri,
                              boolean renderImages,
                              boolean loadRemoteImages) {
        this.baseFontSize = baseFontSize;
        this.baseFontFamily = baseFontFamily;
        this.monospaceFontFamily = monospaceFontFamily;
        this.linkHandler = linkHandler;
        this.baseUri = baseUri;
        this.renderImages = renderImages;
        this.loadRemoteImages = loadRemoteImages;
    }

    /// Default options: system font and size, links ignored, `file:`/`data:` images rendered,
    /// remote images not loaded, no base URI.
    ///
    /// @return the default options
    public static HtmlRenderOptions defaults() {
        return new HtmlRenderOptions(-1, null, "Monospaced", url -> {
        }, null, true, false);
    }

    /// Base font size in points/pixels; unset means [Font#getDefault()]'s size.
    public HtmlRenderOptions withBaseFontSize(double newBaseFontSize) {
        return new HtmlRenderOptions(newBaseFontSize, baseFontFamily, monospaceFontFamily, linkHandler, baseUri, renderImages, loadRemoteImages);
    }

    /// Font family for regular text; unset means [Font#getDefault()]'s family.
    public HtmlRenderOptions withBaseFontFamily(String newBaseFontFamily) {
        return new HtmlRenderOptions(baseFontSize, newBaseFontFamily, monospaceFontFamily, linkHandler, baseUri, renderImages, loadRemoteImages);
    }

    /// Font family for `code`/`pre` content. Default: `"Monospaced"` (JavaFX logical font).
    public HtmlRenderOptions withMonospaceFontFamily(String newMonospaceFontFamily) {
        return new HtmlRenderOptions(baseFontSize, baseFontFamily, Objects.requireNonNull(newMonospaceFontFamily), linkHandler, baseUri, renderImages, loadRemoteImages);
    }

    /// Invoked with the (resolved) `href` when a link is clicked. Default: no-op.
    public HtmlRenderOptions withLinkHandler(Consumer<String> newLinkHandler) {
        return new HtmlRenderOptions(baseFontSize, baseFontFamily, monospaceFontFamily, Objects.requireNonNull(newLinkHandler), baseUri, renderImages, loadRemoteImages);
    }

    /// Base URI against which relative `href`/`src` values are resolved
    /// (replacement for the `<base href>` JabRef injects for WebView today).
    public HtmlRenderOptions withBaseUri(String newBaseUri) {
        return new HtmlRenderOptions(baseFontSize, baseFontFamily, monospaceFontFamily, linkHandler, newBaseUri, renderImages, loadRemoteImages);
    }

    /// Disable to skip `<img>` entirely (useful in tests and tooltips).
    public HtmlRenderOptions withRenderImages(boolean newRenderImages) {
        return new HtmlRenderOptions(baseFontSize, baseFontFamily, monospaceFontFamily, linkHandler, baseUri, newRenderImages, loadRemoteImages);
    }

    /// Allow `http(s):` image sources. Off by default; `file:`/`data:`/`jar:` always load.
    public HtmlRenderOptions withLoadRemoteImages(boolean newLoadRemoteImages) {
        return new HtmlRenderOptions(baseFontSize, baseFontFamily, monospaceFontFamily, linkHandler, baseUri, renderImages, newLoadRemoteImages);
    }

    /// @return the configured base font size, or a non-positive value if unset
    public double baseFontSize() {
        return baseFontSize;
    }

    /// @return the configured base font family, or `null` if unset
    public String baseFontFamily() {
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
    public String baseUri() {
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

    double resolvedBaseFontSize() {
        return baseFontSize > 0 ? baseFontSize : Font.getDefault().getSize();
    }

    String resolvedBaseFontFamily() {
        return baseFontFamily != null ? baseFontFamily : Font.getDefault().getFamily();
    }
}
