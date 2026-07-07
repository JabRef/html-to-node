package org.jabref.htmltonode.rich;

import jfx.incubator.scene.control.richtext.RichTextArea;
import jfx.incubator.scene.control.richtext.StyleHandlerRegistry;
import jfx.incubator.scene.control.richtext.model.StyledTextModel;
import jfx.incubator.scene.control.richtext.skin.CellContext;

/// A [RichTextArea] that renders links with the hand cursor.
///
/// The incubator control gives every segment's `Text` node the default text (I-beam) cursor, so a
/// link would otherwise be indistinguishable from ordinary selectable text on hover — unlike
/// [org.jabref.htmltonode.FxRenderer], whose link runs carry [javafx.scene.Cursor#HAND]. The
/// control has no built-in notion of links; the recommended way to react to a custom character
/// attribute is to extend its [StyleHandlerRegistry] (see the incubator docs). This subclass adds a
/// segment handler for [RichTextRenderer#HREF] that appends an `-fx-cursor: hand` inline style via
/// the [CellContext], exactly as the control's own attribute handlers do. Routing it through the
/// cell context (rather than setting the cursor on the node directly) means the skin re-applies it
/// on every layout and, because it replaces a segment node's whole inline style, a recycled node
/// reused for a non-link run does not keep a stale hand cursor.
public final class HtmlRichTextArea extends RichTextArea {

    /// Mirrors the `-fx-...:value;` shape the control's built-in handlers append to the cell style.
    private static final String HAND_CURSOR_STYLE = "-fx-cursor:hand;";

    private static final StyleHandlerRegistry REGISTRY = buildRegistry();

    public HtmlRichTextArea() {
        super();
    }

    public HtmlRichTextArea(StyledTextModel model) {
        super(model);
    }

    @Override
    public StyleHandlerRegistry getStyleHandlerRegistry() {
        return REGISTRY;
    }

    private static StyleHandlerRegistry buildRegistry() {
        // builder(parent) seeds the new registry with all of the control's default handlers, so
        // bold/italic/color/… keep working; we only add link-cursor handling on top.
        StyleHandlerRegistry.Builder builder = StyleHandlerRegistry.builder(RichTextArea.styleHandlerRegistry);
        builder.setSegHandler(RichTextRenderer.HREF,
                (RichTextArea control, CellContext context, String href) -> context.addStyle(HAND_CURSOR_STYLE));
        return builder.build();
    }
}
