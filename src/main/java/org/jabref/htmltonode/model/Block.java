package org.jabref.htmltonode.model;

import java.util.List;

/// Block-level content resulting from parsing HTML.
///
/// CSS classes from the source HTML (e.g. citeproc's `csl-entry`) are preserved on blocks
/// so renderers can expose them as JavaFX style classes.
public sealed interface Block {

    /// A paragraph of inline content. `spaced` distinguishes `<p>` (vertical margins)
    /// from `<div>`/anonymous inline content (no margins).
    record Paragraph(List<Inline> inlines, boolean spaced, List<String> cssClasses) implements Block {
    }

    /// `<h1>`–`<h6>`; `level` is 1–6.
    record Heading(int level, List<Inline> inlines, List<String> cssClasses) implements Block {
    }

    /// `<ul>` / `<ol start="…">`.
    record ListBlock(boolean ordered, int start, List<ListItem> items) implements Block {
    }

    record ListItem(List<Block> blocks) {
    }

    /// `<dl>` as a flat sequence of terms and details.
    record DefinitionList(List<DefinitionItem> items) implements Block {
    }

    /// `term == true` for `<dt>`, `false` for `<dd>` (rendered indented).
    record DefinitionItem(boolean term, List<Block> blocks) {
    }

    /// `<blockquote>`.
    record Quote(List<Block> blocks) implements Block {
    }

    /// `<pre>`: whitespace preserved, monospace already applied to the runs' styles.
    record Pre(List<Inline> inlines, List<String> cssClasses) implements Block {
    }

    /// `<hr>`.
    record Rule() implements Block {
    }

    /// Minimal `<table>` support: rows of cells, no row spans.
    record Table(List<TableRow> rows) implements Block {
    }

    record TableRow(List<TableCell> cells) {
    }

    record TableCell(boolean header, int columnSpan, List<Block> blocks) {
    }

    /// A grouping block (`<div>` with multiple children, `csl-bib-body`, …).
    record Container(List<Block> blocks, List<String> cssClasses) implements Block {
    }
}
