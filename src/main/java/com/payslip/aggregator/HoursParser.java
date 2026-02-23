package com.payslip.aggregator;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses extracted text from payslips to find hours worked and pay period
 * dates.
 * Uses a broad set of regex patterns to handle different payslip formats.
 */
public class HoursParser {

    // ========================= HOURS PATTERNS =========================

    // Pattern: "Total Hours: 40.00", "Hours Worked: 37.5", "Hrs Worked 40"
    private static final Pattern TOTAL_HOURS_PATTERN = Pattern.compile(
            "(?i)(?:total\\s+hours|hours?\\s+worked|hrs\\s+worked|total\\s+hrs)\\s*:?\\s*(\\d+\\.?\\d*)");

    // Pattern: "Regular Hours: 80.00", "Reg Hours: 40", "Regular Hrs 40.00"
    private static final Pattern REGULAR_HOURS_PATTERN = Pattern.compile(
            "(?i)(?:regular\\s+hours?|reg(?:ular)?\\s+hrs?|standard\\s+hours?|normal\\s+hours?|base\\s+hours?)\\s*:?\\s*(\\d+\\.?\\d*)");

    // Pattern: "Overtime Hours: 8.00", "OT Hours: 4", "O/T Hrs 8.50"
    private static final Pattern OVERTIME_HOURS_PATTERN = Pattern.compile(
            "(?i)(?:overtime\\s+hours?|over\\s*time\\s+hrs?|o/?t\\s+hours?|o/?t\\s+hrs?|ot\\s+hours?)\\s*:?\\s*(\\d+\\.?\\d*)");

    // Pattern: "HOURS 37.5" (standalone label)
    private static final Pattern HOURS_LABEL_PATTERN = Pattern.compile(
            "(?i)(?:^|\\s)hours?\\s*:?\\s+(\\d+\\.?\\d*)(?:\\s|$)");

    // Pattern for tabular: "Regular 80.00" on its own, or "REG 40.00"
    private static final Pattern TABULAR_REG_PATTERN = Pattern.compile(
            "(?i)(?:^|\\s)(?:regular|reg)\\s+(\\d+\\.?\\d*)");

    // Pattern for tabular OT: "OT 8.00", "Overtime 12.50"
    private static final Pattern TABULAR_OT_PATTERN = Pattern.compile(
            "(?i)(?:^|\\s)(?:overtime|o/?t)\\s+(\\d+\\.?\\d*)");

    // ========================= DATE PATTERNS =========================

    // "Pay Period: 01/01/2025 - 01/15/2025" or "Period: 01/01/2025 to 01/15/2025"
    private static final Pattern PAY_PERIOD_RANGE_PATTERN = Pattern.compile(
            "(?i)(?:pay\\s+)?period\\s*:?\\s*(\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4})\\s*(?:to|[-–—])\\s*(\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4})");

    // "Week Ending: 01/07/2025" or "Period End: 01/15/2025" or "Period Ending:
    // 01/15/2025"
    private static final Pattern PERIOD_END_PATTERN = Pattern.compile(
            "(?i)(?:week\\s+end(?:ing)?|period\\s+end(?:ing)?|pay\\s+end(?:ing)?|w/?e)\\s*:?\\s*(\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4})");

    // "Pay Date: 2025-01-15" (ISO format)
    private static final Pattern PAY_DATE_ISO_PATTERN = Pattern.compile(
            "(?i)(?:pay\\s+date|payment\\s+date|date\\s+paid)\\s*:?\\s*(\\d{4}[/\\-]\\d{1,2}[/\\-]\\d{1,2})");

    // "Pay Date: 01/15/2025" or "Pay Date: 01-15-2025"
    private static final Pattern PAY_DATE_US_PATTERN = Pattern.compile(
            "(?i)(?:pay\\s+date|payment\\s+date|date\\s+paid|check\\s+date)\\s*:?\\s*(\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4})");

    // "Period End: Jan 15, 2025" or "Week Ending: January 7, 2025"
    private static final Pattern PERIOD_END_NAMED_MONTH_PATTERN = Pattern.compile(
            "(?i)(?:week\\s+end(?:ing)?|period\\s+end(?:ing)?|pay\\s+date)\\s*:?\\s*" +
                    "((?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\.?\\s+\\d{1,2},?\\s+\\d{4})");

    // Date formatters for parsing
    private static final DateTimeFormatter[] US_DATE_FORMATS = {
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("MM-dd-yyyy"),
            DateTimeFormatter.ofPattern("M-d-yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yy"),
            DateTimeFormatter.ofPattern("M/d/yy"),
    };

    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final DateTimeFormatter[] NAMED_MONTH_FORMATS = {
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMMM d yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM d yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM. d, yyyy", Locale.ENGLISH),
    };

    /**
     * Parse the extracted text from a payslip and return the results.
     * 
     * @param fileName name of the source PDF
     * @param text     extracted text content
     * @return PayslipResult with parsed hours and dates
     */
    public PayslipResult parse(String fileName, String text) {
        List<String> matchedLines = new ArrayList<>();
        double regularHours = 0;
        double overtimeHours = 0;
        double totalHours = 0;
        boolean foundTotal = false;
        boolean foundRegular = false;

        String[] lines = text.split("\\r?\\n");

        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty())
                continue;

            // Try total hours
            Matcher m = TOTAL_HOURS_PATTERN.matcher(trimmedLine);
            if (m.find()) {
                double val = Double.parseDouble(m.group(1));
                if (val > 0) {
                    totalHours += val;
                    foundTotal = true;
                    matchedLines.add("[TOTAL] " + trimmedLine);
                    continue;
                }
            }

            // Try regular hours
            m = REGULAR_HOURS_PATTERN.matcher(trimmedLine);
            if (m.find()) {
                double val = Double.parseDouble(m.group(1));
                if (val > 0) {
                    regularHours += val;
                    foundRegular = true;
                    matchedLines.add("[REG] " + trimmedLine);
                }
            }

            // Try overtime hours (may be on same line as regular)
            m = OVERTIME_HOURS_PATTERN.matcher(trimmedLine);
            if (m.find()) {
                double val = Double.parseDouble(m.group(1));
                if (val > 0) {
                    overtimeHours += val;
                    matchedLines.add("[OT] " + trimmedLine);
                }
            }

            // Try tabular regular
            if (!foundRegular) {
                m = TABULAR_REG_PATTERN.matcher(trimmedLine);
                if (m.find()) {
                    double val = Double.parseDouble(m.group(1));
                    if (val > 0 && val <= 744) { // max hours in a month
                        regularHours += val;
                        foundRegular = true;
                        matchedLines.add("[REG-TAB] " + trimmedLine);
                    }
                }
            }

            // Try tabular OT
            m = TABULAR_OT_PATTERN.matcher(trimmedLine);
            if (m.find()) {
                double val = Double.parseDouble(m.group(1));
                if (val > 0 && val <= 200) {
                    overtimeHours += val;
                    matchedLines.add("[OT-TAB] " + trimmedLine);
                }
            }

            // Try standalone HOURS label
            if (!foundTotal && !foundRegular) {
                m = HOURS_LABEL_PATTERN.matcher(trimmedLine);
                if (m.find()) {
                    double val = Double.parseDouble(m.group(1));
                    if (val > 0 && val <= 744) {
                        totalHours += val;
                        foundTotal = true;
                        matchedLines.add("[HOURS] " + trimmedLine);
                    }
                }
            }
        }

        // If we found regular/OT but no explicit total, calculate it
        if (!foundTotal && foundRegular) {
            totalHours = regularHours + overtimeHours;
        }
        // If we found a total but no reg/OT breakdown, treat total as regular
        if (foundTotal && !foundRegular) {
            regularHours = totalHours - overtimeHours;
        }

        // Parse dates
        LocalDate periodStart = null;
        LocalDate periodEnd = null;

        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty())
                continue;

            // Try pay period range
            Matcher dm = PAY_PERIOD_RANGE_PATTERN.matcher(trimmedLine);
            if (dm.find()) {
                periodStart = parseDate(dm.group(1));
                periodEnd = parseDate(dm.group(2));
                if (periodStart != null || periodEnd != null) {
                    matchedLines.add("[DATE-RANGE] " + trimmedLine);
                    break;
                }
            }

            // Try period end date
            dm = PERIOD_END_PATTERN.matcher(trimmedLine);
            if (dm.find()) {
                periodEnd = parseDate(dm.group(1));
                if (periodEnd != null) {
                    matchedLines.add("[DATE-END] " + trimmedLine);
                    break;
                }
            }

            // Try named month end date
            dm = PERIOD_END_NAMED_MONTH_PATTERN.matcher(trimmedLine);
            if (dm.find()) {
                periodEnd = parseNamedMonthDate(dm.group(1));
                if (periodEnd != null) {
                    matchedLines.add("[DATE-END] " + trimmedLine);
                    break;
                }
            }

            // Try pay date ISO
            dm = PAY_DATE_ISO_PATTERN.matcher(trimmedLine);
            if (dm.find()) {
                periodEnd = parseIsoDate(dm.group(1));
                if (periodEnd != null) {
                    matchedLines.add("[DATE-PAY] " + trimmedLine);
                    break;
                }
            }

            // Try pay date US format
            dm = PAY_DATE_US_PATTERN.matcher(trimmedLine);
            if (dm.find()) {
                periodEnd = parseDate(dm.group(1));
                if (periodEnd != null) {
                    matchedLines.add("[DATE-PAY] " + trimmedLine);
                    break;
                }
            }
        }

        return new PayslipResult(fileName, periodStart, periodEnd,
                regularHours, overtimeHours, totalHours, matchedLines);
    }

    // ========================= DATE PARSING HELPERS =========================

    private LocalDate parseDate(String dateStr) {
        dateStr = dateStr.trim();
        for (DateTimeFormatter fmt : US_DATE_FORMATS) {
            try {
                LocalDate date = LocalDate.parse(dateStr, fmt);
                // Fix 2-digit years
                if (date.getYear() < 100) {
                    date = date.plusYears(2000);
                }
                return date;
            } catch (DateTimeParseException ignored) {
            }
        }
        // Try ISO format too
        return parseIsoDate(dateStr);
    }

    private LocalDate parseIsoDate(String dateStr) {
        dateStr = dateStr.trim().replace("/", "-");
        try {
            return LocalDate.parse(dateStr, ISO_FORMAT);
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    private LocalDate parseNamedMonthDate(String dateStr) {
        dateStr = dateStr.trim();
        for (DateTimeFormatter fmt : NAMED_MONTH_FORMATS) {
            try {
                return LocalDate.parse(dateStr, fmt);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }
}
