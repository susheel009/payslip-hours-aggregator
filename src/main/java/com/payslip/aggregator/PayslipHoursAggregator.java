package com.payslip.aggregator;

import com.payslip.aggregator.config.ConfigManager;
import com.payslip.aggregator.model.PayslipResult;
import com.payslip.aggregator.parser.HoursParser;
import com.payslip.aggregator.parser.PdfTextExtractor;
import com.payslip.aggregator.report.ReportGenerator;

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

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║     Payslip Hours Aggregator v1.0        ║");
        System.out.println("╚══════════════════════════════════════════╝");
        System.out.println();

        // Initialize Config Manager
        ConfigManager config = new ConfigManager();

        // Discover PDF files
        List<File> pdfFiles = discoverPdfFiles(args, config);

        if (pdfFiles.isEmpty()) {
            System.out.println("[WARN] No PDF files found!");
            System.out.println("Usage:");
            System.out.println("  java -jar payslip-hours-aggregator.jar [directory]");
            System.out.println("  java -jar payslip-hours-aggregator.jar file1.pdf file2.pdf ...");
            System.out.println();
            System.out.println("Or place PDFs in configured directories (e.g. 'payslips') and run without arguments.");
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
        reporter.generateReport(results, config.getReportFilePath());
    }

    /**
     * Discover PDF files from command-line arguments.
     * Supports: directory path, list of file paths, or configured directories.
     */
    private static List<File> discoverPdfFiles(String[] args, ConfigManager config) {
        List<File> pdfFiles = new ArrayList<>();

        if (args.length == 0) {
            // Use configured directories
            List<String> dirs = config.getPdfDirectories();
            for (String dirStr : dirs) {
                pdfFiles.addAll(findPdfsInDirectory(new File(dirStr)));
            }
            return pdfFiles;
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
