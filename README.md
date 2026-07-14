# html-to-node

A JavaFX library that renders HTML as plain scene-graph nodes (`TextFlow`, `Text`, `ImageView`),
with no dependency on `javafx.web` or `javafx.controls`.

It was created to replace `WebView` in [JabRef](https://github.com/JabRef/jabref)'s entry preview
(`PreviewViewer`) so that JabRef can drop the `javafx.web` module. It therefore focuses on the HTML
that occurs there: user-defined preview layouts, CSL bibliography output produced by
[citeproc-java](https://github.com/michel-kraemer/citeproc-java), Markdown converted by
[flexmark-java](https://github.com/vsch/flexmark-java), and JabRef's search-highlight `<mark>`
markup.

## Requirements

- Java 24 or later
- JavaFX 26 or later, provided by the consuming application: `javafx.base`, `javafx.graphics`,
  and — for the RichTextArea renderer — `javafx.controls`, `jfx.incubator.input`, and
  `jfx.incubator.richtext`

The library is a JPMS module (`org.jabref.htmltonode`) and is null-marked with
[JSpecify](https://jspecify.dev/) annotations. Its runtime dependencies are
[jsoup](https://jsoup.org/) and the JSpecify annotations; JavaFX is intentionally not declared
in the POM because OpenJFX artifacts are platform-specific.

## Installation

Snapshots are published to the
Released versions are available from [Maven Central](https://central.sonatype.com/artifact/org.jabref/html-to-node):

```kotlin
dependencies {
    implementation("org.jabref:html-to-node:0.1.0")
}
```

Snapshots are published to the
[Maven Central snapshot repository](https://central.sonatype.com/repository/maven-snapshots/)
on every push to `main`:

```kotlin
repositories {
    maven("https://central.sonatype.com/repository/maven-snapshots/")
}
```

When developing locally, install into the local Maven repository instead:

```bash
./gradlew publishToMavenLocal
```

Then, in the consuming build:

```kotlin
repositories {
    mavenLocal()
}

dependencies {
    implementation("org.jabref:html-to-node:0.1.0-SNAPSHOT")
}
```

```java
// module-info.java of the consumer
requires org.jabref.htmltonode;
```

When developing the library and a consumer in parallel, a
[composite build](https://docs.gradle.org/current/userguide/composite_builds.html) avoids
republishing after every change: add `includeBuild("../html-to-node")` to the consumer's
`settings.gradle.kts`, and Gradle substitutes the coordinates with the source build.

## Usage

```java
import javafx.scene.layout.Region;
import org.jabref.htmltonode.HtmlRenderOptions;
import org.jabref.htmltonode.HtmlToNode;
import org.jabref.htmltonode.HtmlView;

// One-shot rendering: HTML string -> Region (place it in a ScrollPane)
Region node = HtmlToNode.render("<b>Kopp, O.</b>: <i>Some Paper</i> (2026)");

// Configured rendering
HtmlRenderOptions options = HtmlRenderOptions.defaults()
        .withBaseFontSize(13)
        .withBaseUri("file:///home/user/papers/")     // resolves relative img/a URLs
        .withLinkHandler(url -> openInBrowser(url));  // receives the resolved href on click
Region preview = HtmlToNode.render(html, options);

// Bindable control: re-renders whenever the html or options property changes
HtmlView view = new HtmlView();
view.setOptions(options);
view.htmlProperty().bind(previewHtmlProperty);

// Plain-text extraction (replacement for WebView's document.body.innerText)
String text = HtmlToNode.toPlainText(html);

// Text can be selected with the mouse; wire the selection to a copy action
Optional<String> selected = view.getSelectedText();
```

Parsing and rendering are separate phases and can be used independently:

```java
List<Block> blocks = HtmlToNode.parse(html);          // pure Java, no JavaFX required
Region node = HtmlToNode.render(blocks, options);
```

API documentation: `./gradlew javadoc` → `build/docs/javadoc/`.

## Supported HTML

**Inline elements.** `b`/`strong`, `i`/`em`/`cite`/`dfn`/`var`, `u`/`ins`, `s`/`strike`/`del`,
`sub`, `sup`, `small`, `big`, `code`/`tt`/`kbd`/`samp`, `mark` (rendered as a true background
highlight via `TextFlow#rangeShape`), `a href` (rendered as a styled `Text` run that wraps
mid-link and invokes the configured link handler), `font` (`face`, `color`, `size`), `br`, and
`img` (`file:`, `data:`, and `jar:` sources; `http(s):` only with `withLoadRemoteImages(true)`).

**Block elements.** `p`, `div`, `h1`–`h6`, `ul`/`ol`/`li`, `dl`/`dt`/`dd`, `blockquote`, `pre`,
`hr`, and minimal `table`/`tr`/`td`/`th` support (rendered as a `GridPane`, including column
spans).

**Inline CSS** (`style` attributes). `font-style`, `font-weight` (keywords and numeric values),
`font-variant: small-caps` (emulated as scaled capitals), `text-decoration`,
`vertical-align: sub | super`, `color`, `background`/`background-color`, `font-size`
(px, pt, em, rem, %, and keywords), `font-family`, `white-space: pre | pre-wrap`,
`display: block`, and `width`/`height` on images.

`<base href>` and the configured base URI are honored when resolving links and image sources.
Unknown elements degrade gracefully — their content is still rendered. `script`, `style`,
`iframe`, and similar elements are ignored. Scripting, external stylesheets, floats, and
positioning are out of scope by design.

## The RichTextArea renderer (optional)

Besides the default TextFlow renderer, the same parsed model can be rendered into the JavaFX
incubator control [RichTextArea](https://openjfx.io/javadoc/26/jfx.incubator.richtext/jfx/incubator/scene/control/richtext/RichTextArea.html)
for native text selection, caret navigation, and accessibility:

```java
import org.jabref.htmltonode.rich.RichHtmlView;

RichHtmlView view = new RichHtmlView();   // scrolls itself - no ScrollPane needed
view.setOptions(options);
view.setHtml(html);
view.getRichTextArea().copy();            // native selection/clipboard
```

The module requires `jfx.incubator.richtext` (which brings `javafx.controls` and
`jfx.incubator.input`). Interim gaps of the rich renderer: sub-/superscript lose
their baseline shift, numeric font weights collapse to bold/normal, and inline images are
skipped (tables and rules are embedded as node paragraphs). Tests for it run via
`./gradlew guiTest` (needs a display or `xvfb-run`).

## Theming

Every rendered node carries CSS style classes (`html-view`, `html-text`, `html-link`,
`html-mark`, `html-h1` … `html-h6`, and so on), and `class` attributes from the source HTML are
passed through (for example citeproc's `csl-entry`). The bundled stylesheet uses Modena's
looked-up colors (`-fx-text-background-color`, `-fx-accent`), so dark themes work through
regular JavaFX CSS — no WebEngine stylesheet injection is required. Any rule can be overridden
from an application stylesheet; explicit colors in the HTML itself are applied as inline styles
and take precedence, matching browser behavior.

## Building and testing

```bash
./gradlew build      # compiles, runs all tests, packages the jars
./gradlew javadoc    # generates API documentation for the exported packages
```

The build uses a JDK 25 toolchain (auto-provisioned) and targets Java 24 bytecode. The test
suite (91 tests) runs headless without a display, Xvfb, or Monocle: since the library never
touches `Scene` or `javafx.controls`, `Text`/`TextFlow` layout and font loading work without
initializing the JavaFX toolkit.

## Integration into JabRef (planned)

Replace the `WebView` inside `PreviewViewer` with an `HtmlView` while keeping the public API:
feed it the `layout.generatePreview(...)` output directly (no `<html><base>` wrapper; pass the
file directory as `baseUri`), print via `PrinterJob.printPage(node)`, and copy text via
`toPlainText`. JabRef's `Highlighter.highlightHtml` output (`<mark style="background: orange">`)
renders as a real highlight. After the preview migration, the remaining `javafx.web` users in
JabRef are the AI summary view (can follow the same approach) and `MathSciNetTab`, which embeds
the live MathSciNet website and therefore needs a product decision (for example, opening the
page in the external browser instead).

## Known limitations

- Text selection covers mouse dragging (across blocks) and is retrieved via
  `HtmlView#getSelectedText()`; keyboard selection and double-click word selection are not
  implemented.
- Table support is minimal: no row spans, no borders, no cell alignment attributes.
- `rem` is treated as `em` (the preview HTML never nests font sizes where this would differ).
- Remote (`http(s):`) images are disabled by default for privacy; local `file:`/`data:` images
  are always rendered.

## License

[MIT](LICENSE)
