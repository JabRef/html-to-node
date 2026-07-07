package org.jabref.htmltonode.internal;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Text;

import org.jabref.htmltonode.model.Inline;

import org.jspecify.annotations.Nullable;
import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;

/// Renders [Inline.Math] via JLaTeXMath into an image-backed node, baseline-aligned with the
/// surrounding text.
public final class MathRendering {

    /// JLaTeXMath rasterizes through Java2D whose antialiasing is coarser than JavaFX text
    /// rendering; drawing at double size and scaling down matches the surrounding glyph quality
    /// (and stays crisp on HiDPI output scales up to 2).
    private static final double SUPERSAMPLE = 2.0;

    private MathRendering() {
    }

    /// @return whether the TeX source parses — callers render [Inline.Math#source()] as plain
    /// text otherwise. Does not require the JavaFX toolkit.
    public static boolean isRenderable(Inline.Math math) {
        try {
            new TeXFormula(math.tex());
            return true;
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /// Creates the node for a math span, or `null` when the TeX does not parse or the image
    /// subsystem is unavailable (fully headless tests). The equation follows the color of the
    /// surrounding text: an explicit run color wins, otherwise the color is looked up via CSS
    /// (style class `html-text`) so themes restyle equations like any other text.
    ///
    /// @param math      the math span
    /// @param fontSize  the effective font size of the surrounding text in pixels
    /// @return the baseline-aligned node with style class `html-math`, or `null`
    public static @Nullable Region createMathNode(Inline.Math math, double fontSize) {
        try {
            TeXFormula formula = new TeXFormula(math.tex());
            TeXIcon icon = formula.createTeXIcon(
                    math.display() ? TeXConstants.STYLE_DISPLAY : TeXConstants.STYLE_TEXT,
                    (float) (fontSize * SUPERSAMPLE));
            if ((icon.getIconWidth() <= 0) || (icon.getIconHeight() <= 0)) {
                return null;
            }
            String explicitColor = math.style().color();
            Color fixedColor = explicitColor == null
                    ? null
                    : RenderSupport.parseColor(explicitColor).orElse(null);
            MathNode node = new MathNode(icon, fixedColor);
            node.getStyleClass().add("html-math");
            return node;
        } catch (RuntimeException | LinkageError e) {
            // invalid TeX, or graphics unavailable: caller falls back to the source text
            return null;
        }
    }

    /// The rendered equation. The image is repainted whenever the effective text color changes,
    /// tracked through an invisible [Text] probe carrying the `html-text` style class — the same
    /// CSS that colors the real text runs.
    private static final class MathNode extends Region {

        private final TeXIcon icon;
        private final ImageView view = new ImageView();
        private final Text colorProbe = new Text();
        private final double width;
        private final double height;
        private final double baseline;

        private MathNode(TeXIcon icon, @Nullable Color fixedColor) {
            this.icon = icon;
            this.width = icon.getIconWidth() / SUPERSAMPLE;
            this.height = icon.getIconHeight() / SUPERSAMPLE;
            this.baseline = (icon.getIconHeight() - icon.getIconDepth()) / SUPERSAMPLE;

            view.setFitWidth(width);
            view.setFitHeight(height);
            view.setSmooth(true);
            view.setManaged(false);
            getChildren().add(view);

            setMinSize(width, height);
            setPrefSize(width, height);
            setMaxSize(width, height);

            if (fixedColor != null) {
                repaint(fixedColor);
            } else {
                colorProbe.getStyleClass().add("html-text");
                colorProbe.setManaged(false);
                colorProbe.setVisible(false);
                getChildren().add(colorProbe);
                colorProbe.fillProperty().addListener((observable, oldFill, newFill) -> repaint(newFill));
                repaint(colorProbe.getFill());
            }
        }

        private void repaint(Paint fill) {
            Color color = fill instanceof Color c ? c : Color.BLACK;
            icon.setForeground(new java.awt.Color(
                    (float) color.getRed(),
                    (float) color.getGreen(),
                    (float) color.getBlue(),
                    (float) color.getOpacity()));
            int imageWidth = icon.getIconWidth();
            int imageHeight = icon.getIconHeight();
            BufferedImage buffer = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = buffer.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                icon.paintIcon(null, graphics, 0, 0);
            } finally {
                graphics.dispose();
            }
            int[] pixels = buffer.getRGB(0, 0, imageWidth, imageHeight, null, 0, imageWidth);
            WritableImage image = new WritableImage(imageWidth, imageHeight);
            image.getPixelWriter().setPixels(0, 0, imageWidth, imageHeight, PixelFormat.getIntArgbInstance(), pixels, 0, imageWidth);
            view.setImage(image);
        }

        @Override
        protected void layoutChildren() {
            view.resizeRelocate(0, 0, width, height);
        }

        @Override
        public double getBaselineOffset() {
            return baseline;
        }
    }
}
