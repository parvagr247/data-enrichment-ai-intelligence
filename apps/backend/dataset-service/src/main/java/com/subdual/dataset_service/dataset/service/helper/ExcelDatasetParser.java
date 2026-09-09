package com.subdual.dataset_service.dataset.service.helper;

import com.subdual.dataset_service.dataset.model.MalformedRow;
import com.subdual.dataset_service.dataset.model.RawDataset;
import org.springframework.stereotype.Component;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Resilient Excel (.xlsx) parser implemented via OpenXML Zip streaming.
 * Reads xl/sharedStrings.xml and xl/worksheets/sheet1.xml without external dependency risks.
 */
@Component
public class ExcelDatasetParser {

    public RawDataset parse(InputStream inputStream, String fileName) throws IOException {
        byte[] bytes = inputStream.readAllBytes();

        List<String> sharedStrings = extractSharedStrings(new ByteArrayInputStream(bytes));
        List<List<String>> sheetRows = extractSheetRows(new ByteArrayInputStream(bytes), sharedStrings);

        if (sheetRows.isEmpty()) {
            return new RawDataset(fileName, List.of(), List.of(), List.of(), Set.of());
        }

        // 1. Extract headers from first non-empty row
        List<String> rawHeaders = sheetRows.get(0);
        List<String> headers = new ArrayList<>();
        for (int i = 0; i < rawHeaders.size(); i++) {
            String h = rawHeaders.get(i).trim();
            if (h.isBlank()) {
                h = "Column_" + (i + 1);
            }
            headers.add(h);
        }

        List<Map<String, String>> validRows = new ArrayList<>();
        List<MalformedRow> malformedRows = new ArrayList<>();
        Set<Integer> duplicateIndices = new LinkedHashSet<>();
        Set<String> seenFingerprints = new HashSet<>();

        // 2. Extract data rows
        for (int r = 1; r < sheetRows.size(); r++) {
            List<String> cells = sheetRows.get(r);
            Map<String, String> row = new LinkedHashMap<>();
            boolean allEmpty = true;
            StringBuilder fingerprint = new StringBuilder();

            for (int c = 0; c < headers.size(); c++) {
                String val = c < cells.size() ? cells.get(c).trim() : "";
                row.put(headers.get(c), val);
                if (!val.isEmpty()) {
                    allEmpty = false;
                }
                fingerprint.append(val.toLowerCase(Locale.ROOT)).append("|");
            }

            if (allEmpty) {
                continue; // Skip blank rows
            }

            String fp = fingerprint.toString();
            if (!seenFingerprints.add(fp)) {
                duplicateIndices.add(validRows.size());
            }

            validRows.add(row);
        }

        return new RawDataset(fileName, headers, validRows, malformedRows, duplicateIndices);
    }

    private List<String> extractSharedStrings(InputStream in) {
        List<String> strings = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("xl/sharedStrings.xml".equalsIgnoreCase(entry.getName())) {
                    Document doc = parseXmlDocument(zis);
                    if (doc != null) {
                        NodeList siNodes = doc.getElementsByTagName("si");
                        for (int i = 0; i < siNodes.getLength(); i++) {
                            Element si = (Element) siNodes.item(i);
                            NodeList tNodes = si.getElementsByTagName("t");
                            StringBuilder sb = new StringBuilder();
                            for (int j = 0; j < tNodes.getLength(); j++) {
                                sb.append(tNodes.item(j).getTextContent());
                            }
                            strings.add(sb.toString());
                        }
                    }
                    break;
                }
            }
        } catch (Exception ignored) {
        }
        return strings;
    }

    private List<List<String>> extractSheetRows(InputStream in, List<String> sharedStrings) {
        List<List<String>> rows = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().startsWith("xl/worksheets/sheet") && entry.getName().endsWith(".xml")) {
                    Document doc = parseXmlDocument(zis);
                    if (doc != null) {
                        NodeList rowNodes = doc.getElementsByTagName("row");
                        for (int i = 0; i < rowNodes.getLength(); i++) {
                            Element rowEl = (Element) rowNodes.item(i);
                            NodeList cNodes = rowEl.getElementsByTagName("c");
                            List<String> rowCells = new ArrayList<>();

                            for (int j = 0; j < cNodes.getLength(); j++) {
                                Element cEl = (Element) cNodes.item(j);
                                String type = cEl.getAttribute("t");
                                NodeList vNodes = cEl.getElementsByTagName("v");
                                String cellVal = "";

                                if (vNodes.getLength() > 0) {
                                    String rawVal = vNodes.item(0).getTextContent();
                                    if ("s".equalsIgnoreCase(type)) {
                                        try {
                                            int idx = Integer.parseInt(rawVal);
                                            if (idx >= 0 && idx < sharedStrings.size()) {
                                                cellVal = sharedStrings.get(idx);
                                            }
                                        } catch (NumberFormatException e) {
                                            cellVal = rawVal;
                                        }
                                    } else {
                                        cellVal = rawVal;
                                    }
                                } else {
                                    NodeList isNodes = cEl.getElementsByTagName("is");
                                    if (isNodes.getLength() > 0) {
                                        cellVal = isNodes.item(0).getTextContent();
                                    }
                                }
                                rowCells.add(cellVal);
                            }
                            if (!rowCells.isEmpty()) {
                                rows.add(rowCells);
                            }
                        }
                    }
                    break;
                }
            }
        } catch (Exception ignored) {
        }
        return rows;
    }

    private Document parseXmlDocument(InputStream in) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                baos.write(buf, 0, len);
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(baos.toByteArray()));
        } catch (Exception e) {
            return null;
        }
    }
}
