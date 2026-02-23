package com.payslip.aggregator.parser;

import com.payslip.aggregator.model.PayslipResult;
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

        String[] lines = text.split("\\r?\\n");
        boolean expectingColumnNumbers = false;

        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty())
                continue;

            String lowerLine = trimmedLine.toLowerCase();

            // Check if this line looks like a header for an hours column
            if (lowerLine.equals("hours") || lowerLine.equals("hrs") || lowerLine.contains("hours")
                    || lowerLine.contains("hrs")) {
                expectingColumnNumbers = true;
            }

            // Keyword based extraction
            if (lowerLine.contains("total") && (lowerLine.contains("hours") || lowerLine.contains("hrs"))) {
                Double val = extractFirstNumber(trimmedLine);
                if (val != null && val > 0) {
                    totalHours += val;
                    matchedLines.add("[TOTAL] " + trimmedLine);
                }
            } else if ((lowerLine.contains("regular") || lowerLine.contains("reg") || lowerLine.contains("standard")
                    || lowerLine.contains("normal") || lowerLine.contains("base"))
                    && (lowerLine.contains("hours") || lowerLine.contains("hrs") || lowerLine.matches(".*\\d+.*"))) {
                Double val = extractFirstNumber(trimmedLine);
                if (val != null && val > 0 && val <= 744) {
                    regularHours += val;
                    matchedLines.add("[REG] " + trimmedLine);
                }
            } else if ((lowerLine.contains("overtime") || lowerLine.contains("over time") || lowerLine.contains("o/t")
                    || lowerLine.contains("ot"))
                    && (lowerLine.contains("hours") || lowerLine.contains("hrs") || lowerLine.matches(".*\\d+.*"))) {
                Double val = extractFirstNumber(trimmedLine);
                if (val != null && val > 0 && val <= 200) {
                    overtimeHours += val;
                    matchedLines.add("[OT] " + trimmedLine);
                }
            } else if (lowerLine.startsWith("hours") || lowerLine.startsWith("hrs")) {
                Double val = extractFirstNumber(trimmedLine);
                if (val != null && val > 0 && val <= 744) {
                    totalHours += val;
                    matchedLines.add("[HOURS] " + trimmedLine);
                }
            } else if (expectingColumnNumbers && trimmedLine.matches("^\\s*\\d{1,3}\\.\\d{1,2}\\s*$")) {
                // "Naked" number parsing from tabular formats
                try {
                    double val = Double.parseDouble(trimmedLine);
                    if (val > 0 && val <= 744) {
                        // Assuming it's regular hours if we don't know better, can refine later
                        regularHours += val;
                        matchedLines.add("[NAKED-NUM] " + trimmedLine);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        // If we found regular/OT but no explicit total, calculate it
        if (totalHours == 0 && (regularHours > 0 || overtimeHours > 0)) {
            totalHours = regularHours + overtimeHours;
        }
        // If we found a total but no reg/OT breakdown, treat total as regular
        if (totalHours > 0 && regularHours == 0) {
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

    /**
     * Extracts the first valid decimal number from a string
     */
    private Double extractFirstNumber(String text) {
        String[] tokens = text.split("\\s+|:");
        for (String token : tokens) {
            try {
                // remove commas if any and parse
                return Double.parseDouble(token.replace(",", ""));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
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
