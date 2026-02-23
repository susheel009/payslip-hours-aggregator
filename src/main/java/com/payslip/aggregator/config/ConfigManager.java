package com.payslip.aggregator.config;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Manages configuration for the application by loading properties from an
 * external file.
 * Falls back to default values if the properties file does not exist.
 */
public class ConfigManager {

    private static final String CONFIG_FILE = "application.properties";
    private static final String DEFAULT_DIRECTORIES = "payslips";
    private static final String DEFAULT_REPORT_FILE = "hours_report.txt";

    private Properties properties;

    public ConfigManager() {
        properties = new Properties();
        loadProperties();
    }

    private void loadProperties() {
        try (java.io.InputStream is = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (is != null) {
                properties.load(is);
                System.out.println("[INFO] Loaded configuration from classpath: " + CONFIG_FILE);
            } else {
                System.out.println(
                        "[INFO] Configuration file " + CONFIG_FILE + " not found on classpath. Using defaults.");
            }
        } catch (IOException e) {
            System.err.println("[WARN] Failed to load configuration from classpath: " + e.getMessage());
        }
    }

    /**
     * Gets a list of configured directory paths to search for PDF files.
     * 
     * @return List of directory paths.
     */
    public List<String> getPdfDirectories() {
        String dirs = properties.getProperty("payslips.directories", DEFAULT_DIRECTORIES);
        return Arrays.stream(dirs.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Gets the configured output report file path.
     * 
     * @return the report file path.
     */
    public String getReportFilePath() {
        return properties.getProperty("report.file", DEFAULT_REPORT_FILE);
    }
}
