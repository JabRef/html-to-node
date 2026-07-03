/// Optional renderer based on the JavaFX incubator RichTextArea control.
///
/// [org.jabref.htmltonode.rich.RichTextRenderer] maps the parsed block model to a styled text
/// model; [org.jabref.htmltonode.rich.RichHtmlView] is the bindable pane with selection access.
/// Rendering requires a running JavaFX toolkit — unlike the default TextFlow-based renderer,
/// which can lay out its nodes without one.
package org.jabref.htmltonode.rich;
