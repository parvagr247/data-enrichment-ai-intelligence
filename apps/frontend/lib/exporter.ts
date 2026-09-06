import * as XLSX from 'xlsx';
import { EnrichedRecord } from '@/types/dataset';

export function exportDataset(
  records: EnrichedRecord[],
  baseFilename: string,
  format: 'csv' | 'xlsx'
): void {
  if (!records || records.length === 0) {
    throw new Error('No records available to export.');
  }

  // Create clean export rows combining original input and enriched values
  const exportRows = records.map((record) => {
    const row: Record<string, string> = { ...record.originalData };

    row['Enrichment_Status'] = record.status;

    const canonicalUrl = record.canonicalUrl || record.response?.result?.canonicalUrl;
    if (canonicalUrl) {
      row['Canonical_Url'] = canonicalUrl;
    }

    if (record.attributes) {
      for (const [key, attr] of Object.entries(record.attributes)) {
        row[`Enriched_${key}`] = attr.value ?? 'UNKNOWN';
        if (attr.confidence) {
          row[`Enriched_${key}_Confidence`] = attr.confidence;
        }
        if (attr.sourceUrl) {
          row[`Enriched_${key}_Source`] = attr.sourceUrl;
        }
      }
    } else if (record.response?.result?.attributes) {
      for (const [key, tuple] of Object.entries(record.response.result.attributes)) {
        row[`Enriched_${key}`] = tuple.value ?? 'UNKNOWN';
        if (tuple.confidence) {
          row[`Enriched_${key}_Confidence`] = tuple.confidence;
        }
        if (tuple.sourceUrl) {
          row[`Enriched_${key}_Source`] = tuple.sourceUrl;
        }
      }
    }

    if (record.errorMessage) {
      row['Enrichment_Error'] = record.errorMessage;
    }

    return row;
  });

  const worksheet = XLSX.utils.json_to_sheet(exportRows);
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, worksheet, 'Enriched Data');

  const cleanBase = baseFilename.replace(/\.[^/.]+$/, '');
  const downloadName = `${cleanBase || 'dataset'}-enriched.${format}`;

  XLSX.writeFile(workbook, downloadName, {
    bookType: format,
  });
}
