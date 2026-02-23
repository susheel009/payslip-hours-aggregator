package com.payslip.aggregator.report;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.WeekFields;
import java.util.*;

import com.payslip.aggregator.constants.ReportConstants;
import com.payslip.aggregator.model.PayslipResult;

/**
 * Generates a console and text-file report from payslip parsing results.
 * Includes a per-file detail table, weekly breakdown table, and grand totals.
 * Utilizes Java 21 Text Blocks for cleaner string formatting.
 */
public class ReportGenerator {

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
        String header = ReportConstants.HEADER_TEMPLATE.formatted(LocalDate.now().format(DATE_FMT));
        sb.append(header);
    }

    // ========================= PER-FILE TABLE =========================

    private void buildPerFileTable(StringBuilder sb, List<PayslipResult> results) {
        String tableHeader = ReportConstants.FILE_TABLE_HEADER.formatted("File Name", "Pay Period", "Reg Hrs", "OT Hrs",
                "Total Hrs");

        sb.append(tableHeader);

        for (PayslipResult r : results) {
            String fileName = truncate(r.getFileName(), 30);
            String period = formatPeriod(r.getPeriodStart(), r.getPeriodEnd());
            sb.append(ReportConstants.FILE_TABLE_ROW.formatted(fileName, period, r.getRegularHours(),
                    r.getOvertimeHours(), r.getTotalHours()));
        }

        sb.append(ReportConstants.FILE_TABLE_FOOTER);
    }

    // ========================= WEEKLY BREAKDOWN =========================

    private void buildWeeklyBreakdown(StringBuilder sb, List<PayslipResult> results) {
        String tableHeader = ReportConstants.WEEKLY_TABLE_HEADER.formatted("Week", "Week Start", "Week End", "Reg Hrs",
                "OT Hrs", "Total Hrs", "Source Files");

        sb.append(tableHeader);

        // Group results by week
        Map<String, WeekData> weekMap = new TreeMap<>();
        List<PayslipResult> undated = new ArrayList<>();

        for (PayslipResult r : results) {
            if (r.getTotalHours() <= 0 && r.getRegularHours() <= 0)
                continue;

            LocalDate endDate = r.getPeriodEnd();
            LocalDate startDate = r.getPeriodStart();

            if (endDate == null && startDate != null) {
                endDate = startDate;
            }

            if (endDate != null) {
                // Determine duration in days
                long days = 7; // default to 1 week
                if (startDate != null) {
                    days = ChronoUnit.DAYS.between(startDate, endDate);
                    if (days <= 0)
                        days = 7;
                }

                int numWeeks = Math.max(1, (int) Math.round(days / 7.0));

                // Proportionally divide hours across the calculated number of weeks
                double regPerWeek = r.getRegularHours() / numWeeks;
                double otPerWeek = r.getOvertimeHours() / numWeeks;
                double totalPerWeek = r.getTotalHours() / numWeeks;

                // Distribute starting from the end date backwards
                for (int i = 0; i < numWeeks; i++) {
                    LocalDate weekDate = endDate.minusWeeks(i);
                    int weekYear = weekDate.get(IsoFields.WEEK_BASED_YEAR);
                    int weekNum = weekDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                    String weekKey = String.format("%d-W%02d", weekYear, weekNum);

                    // Calculate week start (Monday) and week end (Sunday)
                    LocalDate weekStart = weekDate.with(WeekFields.ISO.dayOfWeek(), 1);
                    LocalDate weekEnd = weekDate.with(WeekFields.ISO.dayOfWeek(), 7);

                    WeekData wd = weekMap.computeIfAbsent(weekKey, k -> new WeekData(k, weekStart, weekEnd));
                    wd.regularHours += regPerWeek;
                    wd.overtimeHours += otPerWeek;
                    wd.totalHours += totalPerWeek;

                    String truncatedName = truncate(r.getFileName(), 15);
                    if (!wd.sourceFiles.contains(truncatedName)) {
                        wd.sourceFiles.add(truncatedName);
                    }
                }
            } else {
                undated.add(r);
            }
        }

        // Print weekly rows
        for (WeekData wd : weekMap.values()) {
            String sources = String.join(", ", wd.sourceFiles);
            sb.append(ReportConstants.WEEKLY_TABLE_ROW.formatted(
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
            sb.append(ReportConstants.WEEKLY_UNDATED_DIVIDER);
            double undatedReg = 0, undatedOt = 0, undatedTotal = 0;
            List<String> undatedFiles = new ArrayList<>();
            for (PayslipResult r : undated) {
                undatedReg += r.getRegularHours();
                undatedOt += r.getOvertimeHours();
                undatedTotal += r.getTotalHours();
                undatedFiles.add(truncate(r.getFileName(), 15));
            }
            String sources = String.join(", ", undatedFiles);
            sb.append(ReportConstants.WEEKLY_TABLE_ROW.formatted("Unknown", "N/A", "N/A", undatedReg, undatedOt,
                    undatedTotal, truncate(sources, 15)));
        }

        sb.append(ReportConstants.WEEKLY_TABLE_FOOTER);
    }

    // ========================= GRAND TOTAL =========================

    private void buildGrandTotal(StringBuilder sb, List<PayslipResult> results) {
        double totalReg = results.stream().mapToDouble(PayslipResult::getRegularHours).sum();
        double totalOt = results.stream().mapToDouble(PayslipResult::getOvertimeHours).sum();
        double totalAll = results.stream().mapToDouble(PayslipResult::getTotalHours).sum();
        int fileCount = results.size();
        int filesWithHours = (int) results.stream().filter(r -> r.getTotalHours() > 0).count();

        String totals = ReportConstants.GRAND_TOTAL_TEMPLATE.formatted(fileCount, filesWithHours, totalReg, totalOt,
                totalAll);

        sb.append(totals);
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
