package org.jabref.htmltonode.model;

/// Inline-level content of a paragraph-like block.
public sealed interface Inline {

    /// A run of text with a single resolved style. Whitespace is already collapsed
    /// (except for runs originating in `pre`/`white-space: pre-wrap` contexts).
    record TextRun(String text, InlineStyle style) implements Inline {
    }

    /// An explicit `<br>` line break.
    record LineBreak() implements Inline {
    }

    /// An `<img>`.
    ///
    /// @param source     resolved (absolute if a base URI was known) image URL
    /// @param width      requested width, or `null` if unset
    /// @param height     requested height, or `null` if unset
    /// @param blockImage `display: block` — rendered on a line of its own
    /// @param alt        alternative text, or `null`
    record Image(String source, CssLength width, CssLength height, boolean blockImage, String alt) implements Inline {
    }
}
