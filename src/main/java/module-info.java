import org.jspecify.annotations.NullMarked;

/// Converts an HTML subset (as produced for JabRef's entry preview) into plain JavaFX nodes,
/// removing the need for `javafx.web` / `WebView`.
///
/// The whole module is [NullMarked]: unless annotated `@Nullable`, nothing is `null`.
@NullMarked
module org.jabref.htmltonode {
    requires transitive javafx.graphics;
    requires transitive org.jspecify;
    requires org.jsoup;

    // Only needed by the optional RichTextArea-based renderer
    requires static jfx.incubator.richtext;

    exports org.jabref.htmltonode;
    exports org.jabref.htmltonode.model;
    exports org.jabref.htmltonode.rich;
}
