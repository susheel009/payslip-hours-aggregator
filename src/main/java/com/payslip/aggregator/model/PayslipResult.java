package com.payslip.aggregator.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Holds the extraction results for a single payslip PDF.
 */
public class PayslipResult {

    private final String fileName;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private double regularHours;
    private double overtimeHours;
    private double totalHours;
    private final List<String> matchedLines;

    public PayslipResult(String fileName, LocalDate periodStart, LocalDate periodEnd,
            double regularHours, double overtimeHours, double totalHours,
            List<String> matchedLines) {
        this.fileName = fileName;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.regularHours = regularHours;
        this.overtimeHours = overtimeHours;
        this.totalHours = totalHours;
        this.matchedLines = matchedLines;
    }

    public String getFileName() {
        return fileName;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public double getRegularHours() {
        return regularHours;
    }

    public double getOvertimeHours() {
        return overtimeHours;
    }

    public double getTotalHours() {
        return totalHours;
    }

    public List<String> getMatchedLines() {
        return matchedLines;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
    }

    public void setRegularHours(double regularHours) {
        this.regularHours = regularHours;
    }

    public void setOvertimeHours(double overtimeHours) {
        this.overtimeHours = overtimeHours;
    }

    public void setTotalHours(double totalHours) {
        this.totalHours = totalHours;
    }

    @Override
    public String toString() {
        return String.format("PayslipResult{file='%s', period=%s to %s, regular=%.2f, OT=%.2f, total=%.2f}",
                fileName, periodStart, periodEnd, regularHours, overtimeHours, totalHours);
    }
}
