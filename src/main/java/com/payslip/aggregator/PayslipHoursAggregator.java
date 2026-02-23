package com.payslip.aggregator;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Payslip Hours Aggregator
 * 
 * Reads PDF payslips from a directory, extracts hours worked from each,
 * and produces an aggregated report (console + text file).
 * 
 * Usage:
 * java -jar payslip-hours-aggregator.jar [directory]
 * java -jar payslip-hours-aggregator.jar [file1.pdf] [file2.pdf] ...
 * 
 * If no arguments are given, looks for PDFs in the "./payslips" directory.
 */
public class PayslipHoursAggregator {

    private static final String DEFAULT_DIR = "payslips";
    private static final String REPORT_FILE = "hours_report.txt";

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║     Payslip Hours Aggregator v1.0       ║");
        System.out.println("╚══════════════════════════════════════════╝");
        System.out.println();

        // Discover PDF files
        List<File> pdfFiles = discoverPdfFiles(args);

        if (pdfFiles.isEmpty()) {
            System.out.println("[WARN] No PDF files found!");
            System.out.println("Usage:");
            System.out.println("  java -jar payslip-hours-aggregator.jar [directory]");
            System.out.println("  java -jar payslip-hours-aggregator.jar file1.pdf file2.pdf ...");
            System.out.println();
            System.out.println("Or place PDFs in the '" + DEFAULT_DIR + "' folder and run without arguments.");
            return;
        }

        System.out.println("[INFO] Found " + pdfFiles.size() + " PDF file(s) to process.\n");

        // Initialize components
        PdfTextExtractor extractor = new PdfTextExtractor();
        HoursParser parser = new HoursParser();
        ReportGenerator reporter = new ReportGenerator();

        // Process each PDF
        List<PayslipResult> results = new ArrayList<>();
        for (File pdf : pdfFiles) {
            String text = extractor.extractText(pdf);
            if (text.isEmpty()) {
                System.out.println("  [SKIP] No text extracted from: " + pdf.getName());
                results.add(new PayslipResult(pdf.getName(), null, null, 0, 0, 0, new ArrayList<>()));
                continue;
            }
            PayslipResult result = parser.parse(pdf.getName(), text);
            results.add(result);
            System.out.println("  [OK] " + result);
        }

        System.out.println();

        // Generate report
        reporter.generateReport(results, REPORT_FILE);
    }

    /**
     * Discover PDF files from command-line arguments.
     * Supports: directory path, list of file paths, or default directory.
     */
    private static List<File> discoverPdfFiles(String[] args) {
        List<File> pdfFiles = new ArrayList<>();

        if (args.length == 0) {
            // Use default directory
            return findPdfsInDirectory(new File(DEFAULT_DIR));
        }

        if (args.length == 1) {
            File target = new File(args[0]);
            if (target.isDirectory()) {
                return findPdfsInDirectory(target);
            } else if (target.isFile() && target.getName().toLowerCase().endsWith(".pdf")) {
                pdfFiles.add(target);
                return pdfFiles;
            }
        }

        // Multiple arguments - treat each as a file path
        for (String arg : args) {
            File f = new File(arg);
            if (f.isFile() && f.getName().toLowerCase().endsWith(".pdf")) {
                pdfFiles.add(f);
            } else if (f.isDirectory()) {
                pdfFiles.addAll(findPdfsInDirectory(f));
            } else {
                System.out.println("[WARN] Skipping invalid path: " + arg);
            }
        }

        return pdfFiles;
    }

    private static List<File> findPdfsInDirectory(File dir) {
        List<File> pdfFiles = new ArrayList<>();
        if (!dir.exists() || !dir.isDirectory()) {
            System.out.println("[WARN] Directory not found: " + dir.getAbsolutePath());
            return pdfFiles;
        }

        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".pdf"));
        if (files != null) {
            Arrays.sort(files); // Sort alphabetically
            pdfFiles.addAll(Arrays.asList(files));
        }
        return pdfFiles;
    }
}
