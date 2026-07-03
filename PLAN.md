# html-to-node — Plan & Progress

Goal: Remove `javafx.web` from JabRef. Step 1: a standalone Gradle **library that converts HTML
(as produced for JabRef's entry preview) into plain JavaFX nodes** (`TextFlow`/`Text`/`ImageView`),
so `PreviewViewer` no longer needs `WebView`.

Working dir: `/export/home/kopp/git-repositories/html-to-node` (this repo).
JabRef checkout: `../jabref` (analyzed at commit `2c9543480d`, 2026-07-03).

## Progress checklist

- [x] Analyze JabRef `javafx.web` usage (see "Findings" below)
- [x] Analyze which HTML reaches the preview (default layout, CSL/citeproc-java, markdown comments)
- [x] Decide tech: jsoup parser → intermediate model → JavaFX renderer (no `javafx.controls` needed)
- [x] Scaffold Gradle project (wrapper 9.5.1, JDK 25 toolchain, release 24, JavaFX 26.0.1, jsoup 1.22.2, JUnit 6)
- [x] Headless smoke test: Text/TextFlow layout works without a display (no glass toolkit)
- [x] Implement model (`Block`/`Inline`/`InlineStyle` with numeric font-weight for CSL's `font-weight: 100`)
- [x] Implement HTML → model parser (jsoup walker, whitespace collapsing incl. `white-space: pre-wrap`, style attribute subset)
- [x] Implement model → JavaFX renderer (TextFlow blocks, links, sub/sup, small-caps, mark-highlight layer, images)
- [x] `HtmlView` convenience control + `html-to-node.css` default stylesheet
- [x] Plain-text extraction (for JabRef "copy as text")
- [x] Tests green: `./gradlew build` headless — 84 tests, 0 failures (no display needed)
- [x] README with usage + JabRef integration notes
- [x] GitHub setup: `Check` workflow (build+tests+javadoc), dependabot (gradle daily, actions weekly), automerge (JabRef-style guards, `GITHUB_TOKEN`)
- [ ] (Optional, later) Demo app for manual visual check (needs a display; `demo/` subproject sketched in README)
- [ ] (Later, in JabRef) Integrate: swap WebView out of `PreviewViewer` behind the existing API

## Findings: where JabRef uses javafx.web

Only JabRef's own code needs it — **no third-party dependency requires `javafx.web`**
(gemsfx 4.2.0, pdfviewfx, controlsfx, unitfx: all no; afterburner.fx's POM mentions it but
JabRef already strips that in `build-logic/.../dependency-rules.gradle.kts` L124).

Call sites (all in `jabgui`):

| Site | Feature | Replaceable by this lib? |
|---|---|---|
| `gui/preview/PreviewViewer` | Entry preview (preview tab, entry editor, CSL style picker cells, tooltips) | **Yes — primary target** |
| `gui/ai/summary/AiSummaryShowingView` | AI summary (markdown→HTML→WebView) | Yes; or reuse `MarkdownTextFlow` like the AI *chat* already does (no WebView there) |
| `gui/collab/entrychange/EntryChangeDetailsView` | External-change diff preview (uses PreviewViewer) | Yes (falls out of PreviewViewer swap) |
| `gui/entryeditor/MathSciNetTab` | Embeds the live MathSciNet **website** | **No** — real browser needed; open externally or drop tab |
| `gui/util/WebViewStore` | Pre-warmed WebView pool | Delete after migration |
| `gui/theme/ThemeManager` + `StyleSheet*` | Injects theme CSS into WebEngines | Obsolete: JavaFX CSS themes nodes directly |
| `gui/edit/EditAction` | Copy routes to WebView selection | Needs selection story (see Roadmap) |
| `JabRefGUI` | WebView warm-up | Delete after migration |

`PreviewViewer` WebEngine features that need replacement (file `PreviewViewer.java`):
- `loadContent(html)` where html = `layout.generatePreview(entry, ctx)` wrapped in
  `<html><head><base href="FILE_DIR/"></head><body id="previewBody"> [cover <img>] <div id="content"> … </div></body></html>`
- link click → `NativeDesktop.openBrowser` (→ renderer link handler)
- search highlight: **Java-side** `Highlighter.highlightHtml` injects `<mark style="background: orange">…</mark>` → lib must render `<mark>` with a real background
- cover image `<img style="border-width:1px; border-style:solid; …; height:12rem" src="file:…">`
- copy plain text (`document.body.innerText`) → lib provides plain-text extraction
- copy selection (JS `window.getSelection()`) → v1: not supported (see Roadmap; JabRef has `SelectableTextFlow` already)
- print (`engine.print(job)`) → print the rendered Node via `PrinterJob.printPage(node)`
- tooltip auto-height JS hack → natural node `prefHeight`, hack becomes unnecessary

## Findings: what HTML occurs in the preview

Sources of preview HTML (see agent analysis; verified in `../jabref`):
1. **Default/user preview layouts** (`TextBasedPreviewLayout`, template in `PreviewPreferences` defaults):
   `<font face="sans-serif">`, `<b>`, `<i>`, `<a>`, `<br>`, `<p>`, `<dd>`, `<em>`, `\begin{…}` layout markup resolved before HTML stage.
2. **CSL styles** via citeproc-java (HTML output): `<div class="csl-bib-body">`, `<div class="csl-entry">`,
   `<div class="csl-left-margin">`/`<div class="csl-right-inline">` (numeric styles, side-by-side!),
   `<i>`, `<b>`, `<sup>`, `<sub>`, `<span style="font-variant:small-caps;">`, `<span style="font-style:normal;">`, entities (`&amp;`, `&#38;`, `&ndash;`…).
3. **Markdown comment fields** via flexmark (`MarkdownFormatter`): `<p>`, `<strong>`, `<em>`, `<ul>/<ol>/<li>`, `<code>`, `<pre>`, `<blockquote>`, `<a>`, `<h1>`–`<h6>`, `<hr>`.
4. **JabRef-injected**: cover `<img>`, `<mark style="background: orange">`, error `<div class="error"><h3><p><small>`.
5. Field values may contain stray user HTML — unknown tags must degrade gracefully (render children, never crash).

## Design

Two-phase, so parsing/model is testable without JavaFX and rendering stays small:

```
String html ──jsoup──▶ List<Block> (model; pure Java) ──FxRenderer──▶ javafx.scene.Node
```

- **Parser** `HtmlToModel`: jsoup DOM walk; HTML whitespace collapsing; inline style stack
  (`b/strong`, `i/em/cite/dfn/var`, `u/ins`, `s/strike/del`, `sub`, `sup`, `small`, `big`,
  `code/tt/kbd/samp`, `mark`, `a[href]`, `font[face|color|size]`, `span[style]`, `img`, `br`);
  CSS inline subset: `font-style`, `font-weight`, `text-decoration`, `font-variant: small-caps`,
  `color`, `background(-color)`, `font-size` (em/%/px/pt/keywords), `font-family`.
  Blocks: `p`, `div` (incl. csl-left-margin/right-inline side-by-side), `h1–h6`, `ul/ol/li`,
  `dl/dt/dd`, `blockquote`, `pre`, `hr`, minimal `table/tr/td/th`. Unknown tags → transparent.
- **Model** (`org.jabref.htmltonode.model`): sealed `Block` (Paragraph, Heading, ListBlock, DefinitionList,
  BlockQuote, Pre, Rule, Table, CslEntry…) + sealed `Inline` (TextRun+`InlineStyle`, LineBreak, InlineImage).
- **Renderer** `FxRenderer` + options `HtmlRenderOptions` (base font size/family, link handler,
  base URI, image enable/loader): blocks → `TextFlow` in a `VBox`; links = styled `Text` runs
  (wrap mid-link like a browser; **no javafx.controls dependency**); sub/sup = smaller font + translateY;
  small-caps = uppercase-at-smaller-size run splitting; `mark`/background = unmanaged highlight layer
  inside the TextFlow using `TextFlow#rangeShape` (real background, like WebView).
- **`HtmlView`**: small Region with `htmlProperty`; drop-in content for a ScrollPane.
- **Theming**: every node gets style classes (`html-view`, `html-paragraph`, `html-text`, `html-link`,
  `html-mark`, `csl-entry`, …); ship `html-to-node.css` with modena-friendly defaults
  (dark mode via normal JavaFX CSS — replaces ThemeManager's WebEngine CSS injection).
- Deliberately out: scripting, external CSS files, floats, absolute positioning, forms, iframes/media.

### Build facts (mirror of JabRef, decided)
- Gradle wrapper 9.5.1 (same as JabRef; dist already in `~/.gradle`), Kotlin DSL.
- JDK toolchain 25 (auto-provisioned Corretto/Temurin in `~/.gradle/jdks`), `options.release = 24`
  (JavaFX 26 class files are Java-24 format; JabRef itself is on 25).
- JavaFX **26.0.1**, only `javafx.base` + `javafx.graphics`, `compileOnly` (+ test runtime),
  platform classifier picked in the build script. Consumers bring their own JavaFX.
- jsoup **1.22.2** (same as JabRef; real JPMS module `org.jsoup`), `implementation`.
- JUnit Jupiter **6.1.1** (JabRef uses junit-bom 6.1.1; no AssertJ, plain assertions).
- JPMS: `module org.jabref.htmltonode { requires transitive javafx.graphics; requires org.jsoup; }`;
  tests run on the classpath (module path off for tests) so they work headless without TestFX.
- Headless testing: **no display needed** — the lib avoids `javafx.controls`/`Scene`/glass entirely;
  `Text`/`TextFlow` layout + font loading work without a toolkit. (JabRef CI uses xvfb; not needed here.)
- License: MIT (JabRef-compatible).
- Group `org.jabref`, artifact `html-to-node`, package/module `org.jabref.htmltonode`.
- Documentation standard: full Javadoc (markdown `///` comments) on the exported API incl.
  `package-info.java` + module comment; `./gradlew javadoc` documents **exported packages only**
  (internal excluded, compiled classes patched in) and is kept warning-free via
  `-Xdoclint:all,-missing -Werror`; javadoc jar published alongside sources.

## JabRef integration sketch (follow-up work, in ../jabref)

1. `PreviewViewer`: replace `WebView` with `HtmlView` in the existing `ScrollPane`;
   keep public API (`setLayout/setEntry/print/copy…`). Feed it `layout.generatePreview(...)` directly
   (no `<html><base>` wrapper; pass base dir as `HtmlRenderOptions.baseUri`, cover image via options or keep the `<img>` prefix).
   `Highlighter.highlightHtml` keeps working (lib renders `<mark>`).
   Print via `PrinterJob.printPage(htmlView)`. Plain-text copy via `HtmlToPlainText`.
2. `AiSummaryShowingView` → `MarkdownTextFlow` (pattern already used by AI chat) or `HtmlView`.
3. Delete `WebViewStore`, WebEngine branches in `ThemeManager`/`StyleSheet*`, WebView warm-up in `JabRefGUI`, WebView branch in `EditAction`.
4. `MathSciNetTab`: needs a product decision — open in external browser (recommended) or keep (would keep javafx.web!).
5. Remove `requires javafx.web` from `jabgui/module-info.java`, drop `org.openjfx:javafx-web` constraint in `versions/build.gradle.kts` (L43) and the `javafx-web`/`jdk-jsobject` special-casing in `dependency-rules.gradle.kts` (L50, L60–68) — that also retires the JDK-8342623 workaround.

## Roadmap / open points

- Text **selection** + "copy selection": JabRef in-tree `SelectableTextFlow` (used by AI chat's
  `MarkdownTextFlow`) is the natural basis; could later move into this lib. Until then: copy-all works.
- `table` support is minimal (GridPane, no spans) — enough for user layouts that still use tables.
- Remote (`http:`) images: off by default (privacy, threading); `file:`/`data:` on. JabRef covers are local files → fine.
- Publishing: `./gradlew publishToMavenLocal` works (`maven-publish`; POM carries jsoup as runtime dep,
  no JavaFX — consumers bring their own). JabRef can consume via `mavenLocal()` or, during development,
  `includeBuild("../html-to-node")` (auto-substitutes `org.jabref:html-to-node`). Maven Central later.

## How to resume

- `cd /export/home/kopp/git-repositories/html-to-node && ./gradlew build` (headless OK; 84 tests)
- Read this file top to bottom; unchecked boxes above are the remaining work.
- End-to-end corpus lives inline in `src/test/java/.../JabRefPreviewCorpusTest.java` and mirrors real
  JabRef preview HTML: default layout output (incl. `PreviewViewer` `<html><base>` wrapper + cover `<img>`),
  CSL/citeproc-java output (numeric IEEE with csl-left-margin/right-inline, small-caps style),
  `Highlighter` `<mark>` output, flexmark markdown output, raw AI-summary-style HTML, BST output, multi-entry.
- `PreviewViewer` swap sites in JabRef (7 instantiations): `PreviewPanel:77`, `MainTableTooltip:24`,
  `StyleSelectDialogView:256` (×3 usages), `DatabaseChangesResolverDialog:98`, `EntryChangeDetailsView:43`,
  `GlobalSearchResultDialog:68`, `PreviewTab:186`.
