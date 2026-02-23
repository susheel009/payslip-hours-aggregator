package com.payslip.aggregator.constants;

/**
 * Holds static report UI templates, headers, and structural constants for
 * printing the payload.
 * Cannot be instantiated.
 */
public final class ReportConstants {

    private ReportConstants() {
        // Prevent instantiation
    }

    public static final String HEADER_TEMPLATE = """

            ╔══════════════════════════════════════════════════════════════════╗
            ║              PAYSLIP HOURS AGGREGATOR – REPORT                  ║
            ║              Generated: %-40s║
            ╚══════════════════════════════════════════════════════════════════╝

            """;

    public static final String FILE_TABLE_HEADER = """
            ┌─────────────────────────────────────────────────────────────────────────────────────────────────────┐
            │                                   PER-FILE DETAIL                                                  │
            ├────────────────────────────────┬─────────────────────────────┬──────────┬──────────┬────────────────┤
            │ %-30s │ %-27s │ %8s │ %8s │ %14s │
            ├────────────────────────────────┼─────────────────────────────┼──────────┼──────────┼────────────────┤
            """;

    public static final String FILE_TABLE_ROW = """
            │ %-30s │ %-27s │ %8.2f │ %8.2f │ %14.2f │
            """;

    public static final String FILE_TABLE_FOOTER = """
            └────────────────────────────────┴─────────────────────────────┴──────────┴──────────┴────────────────┘

            """;

    public static final String WEEKLY_TABLE_HEADER = """
            ┌─────────────────────────────────────────────────────────────────────────────────────────────────────┐
            │                                WEEKLY HOURS BREAKDOWN                                              │
            ├──────────────┬──────────────┬──────────────┬──────────┬──────────┬────────────────┬─────────────────┤
            │ %-12s │ %-12s │ %-12s │ %8s │ %8s │ %14s │ %-15s │
            ├──────────────┼──────────────┼──────────────┼──────────┼──────────┼────────────────┼─────────────────┤
            """;

    public static final String WEEKLY_TABLE_ROW = """
            │ %-12s │ %-12s │ %-12s │ %8.2f │ %8.2f │ %14.2f │ %-15s │
            """;

    public static final String WEEKLY_UNDATED_DIVIDER = "├──────────────┼──────────────┼──────────────┼──────────┼──────────┼────────────────┼─────────────────┤\n";

    public static final String WEEKLY_TABLE_FOOTER = """
            └──────────────┴──────────────┴──────────────┴──────────┴──────────┴────────────────┴─────────────────┘

            """;

    public static final String GRAND_TOTAL_TEMPLATE = """
            ╔══════════════════════════════════════════════════════╗
            ║                    GRAND TOTAL                       ║
            ╠══════════════════════════════════════════════════════╣
            ║   Files Processed:   %5d                          ║
            ║   Files with Hours:  %5d                          ║
            ║   Regular Hours:     %9.2f                        ║
            ║   Overtime Hours:    %9.2f                        ║
            ║   ─────────────────────────────────               ║
            ║   TOTAL HOURS:       %9.2f                        ║
            ╚══════════════════════════════════════════════════════╝

            """;
}
