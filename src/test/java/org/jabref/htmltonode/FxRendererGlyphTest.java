package org.jabref.htmltonode;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import org.jabref.htmltonode.rich.RichHtmlView;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies that the CSS applied to a bold HTML run resolves to the same JavaFX glyphs as a
/// directly configured [Text] node.
///
/// The comparison is saved at `build/reports/html-bold-run.png`,
/// `build/reports/direct-javafx-bold.png`, `build/reports/richtext-html-bold.png`, and
/// `build/reports/webview-html-bold.png`. A labeled comparison of the three renderers is saved at
/// `build/reports/glyph-renderer-comparison.png`. This needs the JavaFX toolkit because the
/// renderer pins the HTML font through inline CSS. Their normalized ARGB pixel differences are
/// reported in `build/reports/glyph-pixel-differences.txt`.
@Tag("gui")
class FxRendererGlyphTest {

    private static final String BOLD_TEXT = "JabRef: BibTeX-based literature management software";
    private static final double FONT_SIZE = 16;
    private static final Path HTML_SCREENSHOT_PATH = Path.of("build", "reports", "html-bold-run.png");
    private static final Path DIRECT_TEXT_SCREENSHOT_PATH = Path.of("build", "reports", "direct-javafx-bold.png");
    private static final Path RICH_TEXT_SCREENSHOT_PATH = Path.of("build", "reports", "richtext-html-bold.png");
    private static final Path WEB_VIEW_SCREENSHOT_PATH = Path.of("build", "reports", "webview-html-bold.png");
    private static final Path RENDERER_COMPARISON_PATH = Path.of("build", "reports", "glyph-renderer-comparison.png");
    private static final Path PIXEL_DIFFERENCE_REPORT_PATH = Path.of("build", "reports", "glyph-pixel-differences.txt");

    @BeforeAll
    static void startToolkit() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyRunning) {
            started.countDown();
        }
        assertTrue(started.await(15, TimeUnit.SECONDS), "JavaFX toolkit failed to start");
    }

    @Test
    void boldRunMatchesDirectJavaFxGlyphAdvanceAfterCss() throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<Text> boldRun = new AtomicReference<>();
        AtomicReference<Text> directText = new AtomicReference<>();
        AtomicReference<WritableImage> htmlScreenshot = new AtomicReference<>();
        AtomicReference<WritableImage> directTextScreenshot = new AtomicReference<>();
        AtomicReference<WritableImage> richTextScreenshot = new AtomicReference<>();
        AtomicReference<WritableImage> webViewScreenshot = new AtomicReference<>();
        AtomicReference<WritableImage> rendererComparisonScreenshot = new AtomicReference<>();
        CountDownLatch rendered = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                String html = "<b>" + BOLD_TEXT + "</b>";
                HtmlRenderOptions options = HtmlRenderOptions.defaults()
                        .withBaseFontFamily("System")
                        .withBaseFontSize(FONT_SIZE);
                VBox view = (VBox) HtmlToNode.render(html, options);
                Text reference = new Text(BOLD_TEXT);
                reference.setFont(Font.font("System", FontWeight.BOLD, FONT_SIZE));
                VBox directTextView = new VBox(reference);
                RichHtmlView richTextView = new RichHtmlView();
                richTextView.setUseContentHeight(true);
                richTextView.setOptions(options);
                richTextView.setHtml(html);
                WebView webView = new WebView();
                webView.setPrefSize(1000, 40);
                webView.setMinSize(1000, 40);
                VBox comparison = new VBox(10, view, directTextView, richTextView, webView);
                Stage stage = new Stage();
                stage.setScene(new Scene(comparison, 1000, 200));
                stage.show();
                comparison.applyCss();
                comparison.layout();
                TextFlow flow = (TextFlow) view.getChildren().getFirst();
                boldRun.set((Text) flow.getChildren().stream()
                                      .filter(Text.class::isInstance)
                                      .findFirst()
                                      .orElseThrow());
                directText.set(reference);
                htmlScreenshot.set(view.snapshot(new SnapshotParameters(), null));
                directTextScreenshot.set(directTextView.snapshot(new SnapshotParameters(), null));
                richTextScreenshot.set(richTextView.snapshot(new SnapshotParameters(), null));
                webView.getEngine().getLoadWorker().stateProperty().addListener((_, _, state) -> {
                    if (state == Worker.State.SUCCEEDED) {
                        Platform.runLater(() -> {
                            try {
                                comparison.applyCss();
                                comparison.layout();
                                webViewScreenshot.set(webView.snapshot(new SnapshotParameters(), null));
                                rendererComparisonScreenshot.set(createRendererComparison(
                                        htmlScreenshot.get(),
                                        richTextScreenshot.get(),
                                        webViewScreenshot.get()));
                            } catch (Throwable throwable) {
                                error.set(throwable);
                            } finally {
                                stage.close();
                                rendered.countDown();
                            }
                        });
                    } else if ((state == Worker.State.CANCELLED) || (state == Worker.State.FAILED)) {
                        error.set(new AssertionError("WebView failed to render the glyph comparison"));
                        stage.close();
                        rendered.countDown();
                    }
                });
                webView.getEngine().loadContent("""
                        <html><head><style>
                        body { margin: 0; font-family: System; font-size: 16px; }
                        </style></head><body><b>%s</b></body></html>
                        """.formatted(BOLD_TEXT));
            } catch (Throwable throwable) {
                error.set(throwable);
                rendered.countDown();
            }
        });

        assertTrue(rendered.await(15, TimeUnit.SECONDS), "FX task timed out");
        if (error.get() != null) {
            throw new AssertionError("FX task failed", error.get());
        }

        writeScreenshot(htmlScreenshot.get(), HTML_SCREENSHOT_PATH);
        writeScreenshot(directTextScreenshot.get(), DIRECT_TEXT_SCREENSHOT_PATH);
        writeScreenshot(richTextScreenshot.get(), RICH_TEXT_SCREENSHOT_PATH);
        writeScreenshot(webViewScreenshot.get(), WEB_VIEW_SCREENSHOT_PATH);
        writeScreenshot(rendererComparisonScreenshot.get(), RENDERER_COMPARISON_PATH);
        writePixelDifferenceReport(htmlScreenshot.get(), directTextScreenshot.get(), richTextScreenshot.get(), webViewScreenshot.get());
        assertEquals(directText.get().getFont(), boldRun.get().getFont());
        assertEquals(directText.get().getLayoutBounds().getWidth(), boldRun.get().getLayoutBounds().getWidth(), 0.01);
    }

    private static void writeScreenshot(WritableImage screenshot, Path screenshotPath) throws IOException {
        Files.createDirectories(screenshotPath.getParent());
        BufferedImage image = new BufferedImage((int) screenshot.getWidth(), (int) screenshot.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, screenshot.getPixelReader().getArgb(x, y));
            }
        }
        assertTrue(ImageIO.write(image, "png", screenshotPath.toFile()), "PNG writer unavailable");
    }

    private static WritableImage createRendererComparison(WritableImage htmlImage,
                                                          WritableImage richTextImage,
                                                          WritableImage webViewImage) {
        VBox comparison = new VBox(10,
                rendererRow("HtmlView (TextFlow)", htmlImage),
                rendererRow("RichHtmlView", richTextImage),
                rendererRow("WebView", webViewImage));
        new Scene(comparison);
        comparison.applyCss();
        comparison.autosize();
        comparison.layout();
        return comparison.snapshot(new SnapshotParameters(), null);
    }

    private static HBox rendererRow(String label, WritableImage image) {
        Text labelText = new Text(label);
        labelText.setStyle("-fx-font-weight: bold;");
        labelText.setWrappingWidth(150);
        return new HBox(10, labelText, new ImageView(image));
    }

    private static void writePixelDifferenceReport(WritableImage htmlImage,
                                                   WritableImage directTextImage,
                                                   WritableImage richTextImage,
                                                   WritableImage webViewImage) throws IOException {
        List<PixelComparison> comparisons = List.of(
                comparePixels("html-bold-run.png", htmlImage, "direct-javafx-bold.png", directTextImage),
                comparePixels("html-bold-run.png", htmlImage, "richtext-html-bold.png", richTextImage),
                comparePixels("html-bold-run.png", htmlImage, "webview-html-bold.png", webViewImage),
                comparePixels("direct-javafx-bold.png", directTextImage, "richtext-html-bold.png", richTextImage),
                comparePixels("direct-javafx-bold.png", directTextImage, "webview-html-bold.png", webViewImage),
                comparePixels("richtext-html-bold.png", richTextImage, "webview-html-bold.png", webViewImage));
        Files.createDirectories(PIXEL_DIFFERENCE_REPORT_PATH.getParent());
        Files.writeString(PIXEL_DIFFERENCE_REPORT_PATH, """
                Pixel comparison of normalized glyph bounds
                Each comparison crops uniform background margins, aligns the glyphs at the top left, and compares ARGB values pixel by pixel.

                %s
                """.formatted(comparisons.stream().map(PixelComparison::toString).collect(java.util.stream.Collectors.joining("\n"))));
    }

    private static PixelComparison comparePixels(String firstName,
                                                 WritableImage first,
                                                 String secondName,
                                                 WritableImage second) {
        GlyphBounds firstBounds = glyphBounds(first);
        GlyphBounds secondBounds = glyphBounds(second);
        int width = Math.max(firstBounds.width(), secondBounds.width());
        int height = Math.max(firstBounds.height(), secondBounds.height());
        int differentPixels = 0;
        int maximumChannelDifference = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int firstArgb = pixelAt(first, firstBounds, x, y);
                int secondArgb = pixelAt(second, secondBounds, x, y);
                if (firstArgb != secondArgb) {
                    differentPixels++;
                    maximumChannelDifference = Math.max(maximumChannelDifference, maximumChannelDifference(firstArgb, secondArgb));
                }
            }
        }
        return new PixelComparison(firstName, secondName, width, height, differentPixels, maximumChannelDifference);
    }

    private static GlyphBounds glyphBounds(WritableImage image) {
        int backgroundArgb = image.getPixelReader().getArgb(0, 0);
        int minX = (int) image.getWidth();
        int minY = (int) image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getPixelReader().getArgb(x, y) != backgroundArgb) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return maxX < 0
                ? new GlyphBounds(0, 0, 0, 0, backgroundArgb)
                : new GlyphBounds(minX, minY, maxX - minX + 1, maxY - minY + 1, backgroundArgb);
    }

    private static int pixelAt(WritableImage image, GlyphBounds bounds, int x, int y) {
        return (x < bounds.width()) && (y < bounds.height())
                ? image.getPixelReader().getArgb(bounds.x() + x, bounds.y() + y)
                : bounds.backgroundArgb();
    }

    private static int maximumChannelDifference(int firstArgb, int secondArgb) {
        int maximumDifference = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int firstChannel = (firstArgb >>> shift) & 0xFF;
            int secondChannel = (secondArgb >>> shift) & 0xFF;
            maximumDifference = Math.max(maximumDifference, Math.abs(firstChannel - secondChannel));
        }
        return maximumDifference;
    }

    private record GlyphBounds(int x, int y, int width, int height, int backgroundArgb) {
    }

    private record PixelComparison(String firstName,
                                   String secondName,
                                   int width,
                                   int height,
                                   int differentPixels,
                                   int maximumChannelDifference) {

        @Override
        public String toString() {
            return "%s vs %s%nNormalized size: %d × %d%nDifferent pixels: %d%nMaximum ARGB channel difference: %d"
                    .formatted(firstName, secondName, width, height, differentPixels, maximumChannelDifference);
        }
    }
}
