# Payslip Hours Aggregator

A fast, robust Java 21 command-line application that parses PDF payslips (both text-based and scanned OCR), extracts "hours worked" data using tokenization, and mathematically divides hours dynamically depending on the pay period duration (weekly, bi-weekly, semi-monthly). 

It outputs a highly readable ASCII table of your aggregated hours both to the console and a generated text file.

---

## 🚀 Features
* **Smart Parsing**: Automatically identifies lines denoting regular or overtime hours and intelligently handles "naked numbers" inside tabular rows.
* **Pay Period Math**: Calculates the number of days between a payslip period. If it spans a bi-weekly timeline, it proportionally splits those 80 hours out into two separate weeks in the final breakdown report format!
* **OCR Support**: Includes fallback OCR processing via Tesseract for legacy or scanned image payslips without an embedded text layer.
* **External Configuration**: Uses a `src/main/resources/application.properties` configuration file, avoiding hard-coded magic values.
* **Modern Java Context**: Employs Java 21 Text Blocks to render extremely clean visual data tables without string builders.

## ⚙️ Prerequisites
1. **Java Development Kit (JDK) 17+** (Code optimized for Java 17 and 21)
2. **Apache Maven 3.x+**
3. **Tesseract OCR Engine** [*(Required for scanned images)*](https://tesseract-ocr.github.io/tessdoc/Installation.html)
   * On Windows: Keep the traineddata under `C:\Program Files\Tesseract-OCR\tessdata`. Remember to add a system environment variable called `TESSDATA_PREFIX` pointing to that folder.

## 🛠️ Configuration
Open `src/main/resources/application.properties` and adapt it to your system.
```properties
# Comma-separated list of directories containing the payslip PDFs
pdf.directories=payslips,archive

# Destination output file name
report.file=hours_report.txt
```
*Note: Any output files generated or default payslip directories are automatically ignored by `.gitignore`.*

## 🏃 Building and Running
To fully compile the application and package its dependencies:
```bash
mvn clean package
```

You can then run the built JAR file manually or via the Maven exec context:
```bash
mvn exec:java "-Dexec.mainClass=com.payslip.aggregator.PayslipHoursAggregator"
```

### Expected Output
The application prints a clean payload tracking the aggregate count of regular and OT hours, a weekly proportional breakdown table, and a grand total of hours found explicitly within the PDFs. This same console log is replicated in your `report.file`.
