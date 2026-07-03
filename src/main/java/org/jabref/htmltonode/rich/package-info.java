/// Optional renderer based on the JavaFX incubator RichTextArea control.
///
/// [org.jabref.htmltonode.rich.RichTextRenderer] maps the parsed block model to a styled text
/// model; [org.jabref.htmltonode.rich.RichHtmlView] is the bindable pane. Using this package
/// requires the `jfx.incubator.richtext` module (declared `requires static` by this module) and
/// a running JavaFX toolkit — unlike the default TextFlow-based renderer, which stays free of
/// `javafx.controls`.
package org.jabref.htmltonode.rich;
