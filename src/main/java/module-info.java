/// Converts an HTML subset (as produced for JabRef's entry preview) into plain JavaFX nodes,
/// removing the need for `javafx.web` / `WebView`.
module org.jabref.htmltonode {
    requires transitive javafx.graphics;
    requires org.jsoup;

    exports org.jabref.htmltonode;
    exports org.jabref.htmltonode.model;
}
