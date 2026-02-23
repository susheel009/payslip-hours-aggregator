package com.payslip.aggregator;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Extracts text from PDF files.
 * First tries direct text extraction via PDFBox.
 * Falls back to OCR via Tesseract (Tess4J) if the PDF appears to be scanned/image-based.
 */
public class PdfTextExtractor {

    private static final int MIN_CHARS_PER_PAGE = 50;
    private static final float OCR_DPI = 300f;

    private final boolean tesseractAvailable;
    private final Tesseract tesseract;

    public PdfTextExtractor() {
        Tesseract t = null;
        boolean available = false;
        try {
            t = new Tesseract();
            // Use Tesseract from system PATH — user has installed it
            // Try to detect tessdata path from TESSDATA_PREFIX env or default locations
            String tessDataPath = System.getenv("TESSDATA_PREFIX");
            if (tessDataPath == null || tessDataPath.isEmpty()) {
                // Common Windows install paths
                String[] commonPaths = {
                    "C:\\Program Files\\Tesseract-OCR\\tessdata",
                    "C:\\Program Files (x86)\\Tesseract-OCR\\tessdata",
                    System.getProperty("user.home") + "\\AppData\\Local\\Tesseract-OCR\\tessdata"
                };
                for (String path : commonPaths) {
                    if (new File(path).exists()) {
                        tessDataPath = path;
                        break;
                    }
                }
            }
            if (tessDataPath != null && !tessDataPath.isEmpty()) {
                // tessdata path should be the parent directory containing "tessdata" folder
                // Tess4J expects the parent of the tessdata folder
                File tessDataDir = new File(tessDataPath);
                if (tessDataDir.getName().equals("tessdata")) {
                    t.setDatapath(tessDataDir.getParent());
                } else {
                    t.setDatapath(tessDataPath);
                }
            }
            t.setLanguage("eng");
            available = true;
            System.out.println("[INFO] Tesseract OCR is available for scanned PDFs.");
        } catch (Exception e) {
            System.out.println("[WARN] Tesseract OCR not available. Scanned PDFs may not be processed.");
            System.out.println("       Install Tesseract and ensure it's on your PATH.");
        }
        this.tesseract = t;
        this.tesseractAvailable = available;
    }

    /**
     * Extract text from the given PDF file.
     * @param pdfFile the PDF file to extract text from
     * @return the extracted text content
     */
    public String extractText(File pdfFile) {
        System.out.println("[INFO] Processing: " + pdfFile.getName());

        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            // First attempt: direct text extraction
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            int pageCount = document.getNumberOfPages();
            if (pageCount == 0) pageCount = 1;

            // Check if text extraction yielded meaningful content
            String trimmed = text.replaceAll("\\s+", "");
            double charsPerPage = (double) trimmed.length() / pageCount;

            if (charsPerPage >= MIN_CHARS_PER_PAGE) {
                System.out.println("  [TEXT] Extracted " + trimmed.length() + " chars via text layer.");
                return text;
            }

            // Fall back to OCR
            if (!tesseractAvailable) {
                System.out.println("  [WARN] PDF appears scanned but Tesseract is not available. Returning sparse text.");
                return text;
            }

            System.out.println("  [OCR] Text layer sparse (" + (int) charsPerPage + " chars/page). Using OCR...");
            return performOcr(document, pdfFile.getName());

        } catch (IOException e) {
            System.err.println("[ERROR] Failed to read PDF: " + pdfFile.getName() + " - " + e.getMessage());
            return "";
        }
    }

    private String performOcr(PDDocument document, String fileName) {
        StringBuilder ocrText = new StringBuilder();
        PDFRenderer renderer = new PDFRenderer(document);
        int pageCount = document.getNumberOfPages();

        for (int i = 0; i < pageCount; i++) {
            try {
                BufferedImage image = renderer.renderImageWithDPI(i, OCR_DPI);
                String pageText = tesseract.doOCR(image);
                ocrText.append(pageText).append("\n");
                System.out.println("  [OCR] Page " + (i + 1) + "/" + pageCount + " processed.");
            } catch (IOException e) {
                System.err.println("  [ERROR] Failed to render page " + (i + 1) + " of " + fileName + ": " + e.getMessage());
            } catch (TesseractException e) {
                System.err.println("  [ERROR] OCR failed on page " + (i + 1) + " of " + fileName + ": " + e.getMessage());
            }
        }

        return ocrText.toString();
    }
}
