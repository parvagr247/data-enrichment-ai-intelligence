package com.subdual.dataset_service.dataset.service.helper;

import com.subdual.dataset_service.dataset.model.MalformedRow;
import com.subdual.dataset_service.dataset.model.RawDataset;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Robust RFC 4180 compliant CSV parser.
 * Handles commas, semicolons, tabs, quoted cells with embedded commas or newlines,
 * escaped quotes (""), and gracefully isolates malformed rows.
 */
@Component
public class CsvDatasetParser {

    public RawDataset parse(InputStream inputStream, String fileName) throws IOException {
        List<String> rawLines = readAllLogicalLines(inputStream);
        if (rawLines.isEmpty()) {
            return new RawDataset(fileName, List.of(), List.of(), List.of(), Set.of());
        }

        char delimiter = detectDelimiter(rawLines.get(0));

        // 1. Parse header row
        List<String> rawHeaderTokens = parseCsvTokens(rawLines.get(0), delimiter);
        List<String> headers = new ArrayList<>();
        for (int i = 0; i < rawHeaderTokens.size(); i++) {
            String h = rawHeaderTokens.get(i).trim();
            if (h.isBlank()) {
                h = "Column_" + (i + 1);
            }
            headers.add(h);
        }

        List<Map<String, String>> validRows = new ArrayList<>();
        List<MalformedRow> malformedRows = new ArrayList<>();
        Set<Integer> duplicateIndices = new LinkedHashSet<>();
        Set<String> seenFingerprints = new HashSet<>();

        // 2. Parse data rows
        for (int i = 1; i < rawLines.size(); i++) {
            String line = rawLines.get(i);
            if (line.trim().isEmpty()) {
                continue; // Skip blank lines
            }

            List<String> tokens = parseCsvTokens(line, delimiter);
            if (tokens.size() != headers.size()) {
                malformedRows.add(new MalformedRow(
                        i,
                        line,
                        "Expected " + headers.size() + " columns but found " + tokens.size()
                ));
                continue;
            }

            Map<String, String> row = new LinkedHashMap<>();
            boolean allEmpty = true;
            StringBuilder fingerprint = new StringBuilder();

            for (int c = 0; c < headers.size(); c++) {
                String val = tokens.get(c).trim();
                row.put(headers.get(c), val);
                if (!val.isEmpty()) {
                    allEmpty = false;
                }
                fingerprint.append(val.toLowerCase(Locale.ROOT)).append("|");
            }

            if (allEmpty) {
                continue; // Skip completely empty data rows
            }

            String fp = fingerprint.toString();
            if (!seenFingerprints.add(fp)) {
                duplicateIndices.add(validRows.size());
            }

            validRows.add(row);
        }

        return new RawDataset(fileName, headers, validRows, malformedRows, duplicateIndices);
    }

    private char detectDelimiter(String headerLine) {
        int commas = countOccurrences(headerLine, ',');
        int semicolons = countOccurrences(headerLine, ';');
        int tabs = countOccurrences(headerLine, '\t');

        if (semicolons > commas && semicolons > tabs) return ';';
        if (tabs > commas && tabs > semicolons) return '\t';
        return ',';
    }

    private int countOccurrences(String str, char ch) {
        int count = 0;
        for (char c : str.toCharArray()) {
            if (c == ch) count++;
        }
        return count;
    }

    private List<String> readAllLogicalLines(InputStream inputStream) throws IOException {
        List<String> logicalLines = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

        StringBuilder currentLogicalLine = new StringBuilder();
        boolean insideQuotes = false;
        String line;

        while ((line = reader.readLine()) != null) {
            int quoteCount = countQuotes(line);
            if (insideQuotes) {
                currentLogicalLine.append("\n").append(line);
                if (quoteCount % 2 != 0) {
                    insideQuotes = false;
                    logicalLines.add(currentLogicalLine.toString());
                    currentLogicalLine.setLength(0);
                }
            } else {
                if (quoteCount % 2 != 0) {
                    insideQuotes = true;
                    currentLogicalLine.append(line);
                } else {
                    logicalLines.add(line);
                }
            }
        }

        if (currentLogicalLine.length() > 0) {
            logicalLines.add(currentLogicalLine.toString());
        }

        return logicalLines;
    }

    private int countQuotes(String s) {
        int count = 0;
        for (char c : s.toCharArray()) {
            if (c == '"') count++;
        }
        return count;
    }

    private List<String> parseCsvTokens(String line, char delimiter) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++; // Skip escaped quote
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == delimiter && !inQuotes) {
                tokens.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString());
        return tokens;
    }
}
