package com.payslip.aggregator;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates a console and text-file report from payslip parsing results.
 * Includes a per-file detail table, weekly breakdown table, and grand totals.
 */
public class ReportGenerator {

    private static final String SEPARATOR_THIN = "─";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    private static final DateTimeFormatter SHORT_DATE_FMT = DateTimeFormatter.ofPattern("MMM dd");

    /**
     * Generate the report to console and a text file.
     * 
     * @param results    list of parsed payslip results
     * @param outputFile path to the output text file
     */
    public void generateReport(List<PayslipResult> results, String outputFile) {
        StringBuilder sb = new StringBuilder();

        buildHeader(sb);
        buildPerFileTable(sb, results);
        buildWeeklyBreakdown(sb, results);
        buildGrandTotal(sb, results);
        buildMatchedLinesSection(sb, results);

        String report = sb.toString();

        // Print to console
        System.out.println(report);

        // Write to file
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            writer.print(report);
            System.out.println("[INFO] Report written to: " + outputFile);
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to write report: " + e.getMessage());
        }
    }

    // ========================= HEADER =========================

    private void buildHeader(StringBuilder sb) {
        sb.append("\n");
        sb.append("╔══════════════════════════════════════════════════════════════════╗\n");
        sb.append("║              PAYSLIP HOURS AGGREGATOR – REPORT                  ║\n");
        sb.append("║              Generated: ").append(String.format("%-40s", LocalDate.now().format(DATE_FMT)))
                .append("║\n");
        sb.append("╚══════════════════════════════════════════════════════════════════╝\n");
        sb.append("\n");
    }

    // ========================= PER-FILE TABLE =========================

    private void buildPerFileTable(StringBuilder sb, List<PayslipResult> results) {
        sb.append(
                "┌─────────────────────────────────────────────────────────────────────────────────────────────────────┐\n");
        sb.append(
                "│                                   PER-FILE DETAIL                                                  │\n");
        sb.append(
                "├────────────────────────────────┬─────────────────────────────┬──────────┬──────────┬────────────────┤\n");
        sb.append(String.format("│ %-30s │ %-27s │ %8s │ %8s │ %14s │%n",
                "File Name", "Pay Period", "Reg Hrs", "OT Hrs", "Total Hrs"));
        sb.append(
                "├────────────────────────────────┼─────────────────────────────┼──────────┼──────────┼────────────────┤\n");

        for (PayslipResult r : results) {
            String fileName = truncate(r.getFileName(), 30);
            String period = formatPeriod(r.getPeriodStart(), r.getPeriodEnd());
            sb.append(String.format("│ %-30s │ %-27s │ %8.2f │ %8.2f │ %14.2f │%n",
                    fileName, period, r.getRegularHours(), r.getOvertimeHours(), r.getTotalHours()));
        }

        sb.append(
                "└────────────────────────────────┴─────────────────────────────┴──────────┴──────────┴────────────────┘\n");
        sb.append("\n");
    }

    // ========================= WEEKLY BREAKDOWN =========================

    private void buildWeeklyBreakdown(StringBuilder sb, List<PayslipResult> results) {
        sb.append(
                "┌─────────────────────────────────────────────────────────────────────────────────────────────────────┐\n");
        sb.append(
                "│                                WEEKLY HOURS BREAKDOWN                                              │\n");
        sb.append(
                "├──────────────┬──────────────┬──────────────┬──────────┬──────────┬────────────────┬─────────────────┤\n");
        sb.append(String.format("│ %-12s │ %-12s │ %-12s │ %8s │ %8s │ %14s │ %-15s │%n",
                "Week", "Week Start", "Week End", "Reg Hrs", "OT Hrs", "Total Hrs", "Source Files"));
        sb.append(
                "├──────────────┼──────────────┼──────────────┼──────────┼──────────┼────────────────┼─────────────────┤\n");

        // Group results by week
        Map<String, WeekData> weekMap = new TreeMap<>();
        List<PayslipResult> undated = new ArrayList<>();

        for (PayslipResult r : results) {
            if (r.getTotalHours() <= 0 && r.getRegularHours() <= 0)
                continue;

            LocalDate endDate = r.getPeriodEnd();
            if (endDate == null && r.getPeriodStart() != null) {
                endDate = r.getPeriodStart();
            }

            if (endDate != null) {
                int weekYear = endDate.get(IsoFields.WEEK_BASED_YEAR);
                int weekNum = endDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                String weekKey = String.format("%d-W%02d", weekYear, weekNum);

                // Calculate week start (Monday) and week end (Sunday)
                LocalDate weekStart = endDate.with(WeekFields.ISO.dayOfWeek(), 1);
                LocalDate weekEnd = endDate.with(WeekFields.ISO.dayOfWeek(), 7);

                WeekData wd = weekMap.computeIfAbsent(weekKey, k -> new WeekData(k, weekStart, weekEnd));
                wd.regularHours += r.getRegularHours();
                wd.overtimeHours += r.getOvertimeHours();
                wd.totalHours += r.getTotalHours();
                wd.sourceFiles.add(truncate(r.getFileName(), 15));
            } else {
                undated.add(r);
            }
        }

        // Print weekly rows
        for (WeekData wd : weekMap.values()) {
            String sources = String.join(", ", wd.sourceFiles);
            sb.append(String.format("│ %-12s │ %-12s │ %-12s │ %8.2f │ %8.2f │ %14.2f │ %-15s │%n",
                    wd.weekLabel,
                    wd.weekStart.format(SHORT_DATE_FMT),
                    wd.weekEnd.format(SHORT_DATE_FMT),
                    wd.regularHours,
                    wd.overtimeHours,
                    wd.totalHours,
                    truncate(sources, 15)));
        }

        // Print undated rows
        if (!undated.isEmpty()) {
            sb.append(
                    "├──────────────┼──────────────┼──────────────┼──────────┼──────────┼────────────────┼─────────────────┤\n");
            double undatedReg = 0, undatedOt = 0, undatedTotal = 0;
            List<String> undatedFiles = new ArrayList<>();
            for (PayslipResult r : undated) {
                undatedReg += r.getRegularHours();
                undatedOt += r.getOvertimeHours();
                undatedTotal += r.getTotalHours();
                undatedFiles.add(truncate(r.getFileName(), 15));
            }
            String sources = String.join(", ", undatedFiles);
            sb.append(String.format("│ %-12s │ %-12s │ %-12s │ %8.2f │ %8.2f │ %14.2f │ %-15s │%n",
                    "Unknown", "N/A", "N/A", undatedReg, undatedOt, undatedTotal,
                    truncate(sources, 15)));
        }

        sb.append(
                "└──────────────┴──────────────┴──────────────┴──────────┴──────────┴────────────────┴─────────────────┘\n");
        sb.append("\n");
    }

    // ========================= GRAND TOTAL =========================

    private void buildGrandTotal(StringBuilder sb, List<PayslipResult> results) {
        double totalReg = results.stream().mapToDouble(PayslipResult::getRegularHours).sum();
        double totalOt = results.stream().mapToDouble(PayslipResult::getOvertimeHours).sum();
        double totalAll = results.stream().mapToDouble(PayslipResult::getTotalHours).sum();
        int fileCount = results.size();
        int filesWithHours = (int) results.stream().filter(r -> r.getTotalHours() > 0).count();

        sb.append("╔══════════════════════════════════════════════════════╗\n");
        sb.append("║                    GRAND TOTAL                      ║\n");
        sb.append("╠══════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║   Files Processed:   %5d                          ║%n", fileCount));
        sb.append(String.format("║   Files with Hours:  %5d                          ║%n", filesWithHours));
        sb.append(String.format("║   Regular Hours:     %9.2f                      ║%n", totalReg));
        sb.append(String.format("║   Overtime Hours:    %9.2f                      ║%n", totalOt));
        sb.append(String.format("║   ─────────────────────────────────                  ║%n"));
        sb.append(String.format("║   TOTAL HOURS:       %9.2f                      ║%n", totalAll));
        sb.append("╚══════════════════════════════════════════════════════╝\n");
        sb.append("\n");
    }

    // ========================= MATCHED LINES (AUDIT) =========================

    private void buildMatchedLinesSection(StringBuilder sb, List<PayslipResult> results) {
        sb.append("─── Matched Lines (Audit Trail) ───────────────────────────\n\n");
        for (PayslipResult r : results) {
            sb.append("  ").append(r.getFileName()).append(":\n");
            if (r.getMatchedLines().isEmpty()) {
                sb.append("    (no hours patterns matched)\n");
            } else {
                for (String line : r.getMatchedLines()) {
                    sb.append("    ").append(line).append("\n");
                }
            }
            sb.append("\n");
        }
        sb.append("───────────────────────────────────────────────────────────\n");
    }

    // ========================= HELPERS =========================

    private String formatPeriod(LocalDate start, LocalDate end) {
        if (start != null && end != null) {
            return start.format(SHORT_DATE_FMT) + " – " + end.format(SHORT_DATE_FMT) + ", " + end.getYear();
        } else if (end != null) {
            return "Ending " + end.format(DATE_FMT);
        } else if (start != null) {
            return "Starting " + start.format(DATE_FMT);
        }
        return "Unknown Period";
    }

    private String truncate(String s, int maxLen) {
        if (s == null)
            return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 2) + "..";
    }

    // Internal class for weekly aggregation
    private static class WeekData {
        String weekLabel;
        LocalDate weekStart;
        LocalDate weekEnd;
        double regularHours;
        double overtimeHours;
        double totalHours;
        List<String> sourceFiles = new ArrayList<>();

        WeekData(String weekLabel, LocalDate weekStart, LocalDate weekEnd) {
            this.weekLabel = weekLabel;
            this.weekStart = weekStart;
            this.weekEnd = weekEnd;
        }
    }
}
