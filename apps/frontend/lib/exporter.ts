import * as XLSX from 'xlsx';
import { EnrichedRecord } from '@/lib/api';

export function exportDataset(
  records: EnrichedRecord[],
  baseFilename: string,
  format: 'csv' | 'xlsx'
): void {
  if (!records || records.length === 0) {
    throw new Error('No records available to export.');
  }

  // Create clean export rows combining original input and enriched values
  const exportRows = records.map(record => {
    const row: Record<string, string> = { ...record.originalData };

    row['Enrichment_Status'] = record.status;
    if (record.response?.entityId) {
      row['Enrichment_Entity_Id'] = record.response.entityId;
    }
    if (record.response?.result?.canonicalUrl) {
      row['Canonical_Url'] = record.response.result.canonicalUrl;
    }

    if (record.response?.result?.attributes) {
      for (const [key, tuple] of Object.entries(record.response.result.attributes)) {
        row[`Enriched_${key}`] = tuple.value ?? 'UNKNOWN';
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
