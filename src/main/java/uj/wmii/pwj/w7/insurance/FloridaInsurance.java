package uj.wmii.pwj.w7.insurance;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.nio.charset.StandardCharsets.UTF_8;

public class FloridaInsurance {
    private static final String ZIP_FILE = "FL_insurance.csv.zip";
    private static final String CSV_FILE = "FL_insurance.csv";
    private static final Path COUNT_FILE = Path.of("count.txt");
    private static final Path TIV2012_FILE = Path.of("tiv2012.txt");
    private static final Path MOST_VALUABLE_FILE = Path.of("most_valuable.txt");

    public static void main(String[] args) {
        try {
            List<InsuranceEntry> entries = loadData();
            generateReports(entries);
        } catch (IOException e) {
            System.err.println("Error processing data: " + e.getMessage());
        }
    }

    private static List<InsuranceEntry> loadData() throws IOException {
        try (ZipFile zip = new ZipFile(ZIP_FILE)) {
            ZipEntry entry = zip.stream()
                    .filter(e -> e.getName().equals(CSV_FILE))
                    .findFirst()
                    .orElseThrow(() -> new IOException("CSV file not found in archive"));

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(zip.getInputStream(entry), UTF_8))) {
                reader.readLine(); // Skip header
                return reader.lines()
                        .map(InsuranceEntry::parse)
                        .toList();
            }
        }
    }

    private static void generateReports(List<InsuranceEntry> entries) {
        writeCountReport(entries);
        writeTivReport(entries);
        writeTopCountiesReport(entries);
    }

    private static void writeCountReport(List<InsuranceEntry> entries) {
        long count = entries.stream()
                .map(e -> e.county)
                .distinct()
                .count();
        writeFile(COUNT_FILE, String.valueOf(count));
    }

    private static void writeTivReport(List<InsuranceEntry> entries) {
        double total = entries.stream()
                .mapToDouble(e -> e.tiv2012)
                .sum();
        writeFile(TIV2012_FILE, String.format(Locale.US, "%.2f", total));
    }

    private static void writeTopCountiesReport(List<InsuranceEntry> entries) {
        List<Map.Entry<String, Double>> top = entries.stream()
                .collect(Collectors.groupingBy(
                        e -> e.county,
                        Collectors.summingDouble(e -> e.tiv2012 - e.tiv2011)))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(10)
                .toList();

        try (BufferedWriter writer = Files.newBufferedWriter(MOST_VALUABLE_FILE)) {
            writer.write("country,value");
            writer.newLine();
            for (Map.Entry<String, Double> entry : top) {
                writer.write(String.format(Locale.US, "%s,%.2f", entry.getKey(), entry.getValue()));
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Error writing to " + MOST_VALUABLE_FILE + ": " + e.getMessage());
        }
    }

    private static void writeFile(Path path, String content) {
        try {
            Files.writeString(path, content);
        } catch (IOException e) {
            System.err.println("Error writing to " + path + ": " + e.getMessage());
        }
    }

    private static class InsuranceEntry {
        final String county;
        final double tiv2011;
        final double tiv2012;

        InsuranceEntry(String county, double tiv2011, double tiv2012) {
            this.county = county;
            this.tiv2011 = tiv2011;
            this.tiv2012 = tiv2012;
        }

        static InsuranceEntry parse(String line) {
            String[] fields = line.split(",");
            return new InsuranceEntry(
                    fields[2],
                    Double.parseDouble(fields[7]),
                    Double.parseDouble(fields[8]));
        }
    }
}