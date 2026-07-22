# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.2.0] - 2026-07-22

### Added

- `RichHtmlView#setHtml(String, HtmlRenderOptions)`, which updates the HTML and the render options with a single model rebuild instead of one per property.

## [0.1.0] - 2026-07-14

### Added

- HTML-to-node rendering (`HtmlToNode.render`): renders the HTML subset produced by [JabRef](https://github.com/JabRef/jabref)'s entry preview — user-defined layouts, CSL output from [citeproc-java](https://github.com/michel-kraemer/citeproc-java), Markdown from [flexmark-java](https://github.com/vsch/flexmark-java), and `<mark>` search highlights — as plain JavaFX `TextFlow`, `Text`, and `ImageView` nodes with no dependency on `javafx.web` or `javafx.controls`.
- `HtmlView` (a `VBox`) and the intermediate block/inline model (`HtmlToNode.parse`) for consumers that want to inspect or post-process the parsed structure before rendering.
- `HtmlRenderOptions` for configuring rendering, including a base URI for resolving relative image and link URLs (and `withoutBaseUri()` to opt out).
- Mouse text selection across rendered blocks in the plain-node renderer.
- RichTextArea-based renderer (`RichHtmlView`, `HtmlRichTextArea`, `RichTextRenderer`) with inline images, link hovering (hand cursor), and content-height sizing. It requires the `jfx.incubator.richtext` and `jfx.incubator.input` incubator modules, declared as `requires static`.
- The module is null-marked with [JSpecify](https://jspecify.dev/) annotations.

[Unreleased]: https://github.com/JabRef/html-to-node/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/JabRef/html-to-node/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/JabRef/html-to-node/releases/tag/v0.1.0
