import * as XLSX from 'xlsx';
import { RawRow } from '@/lib/api';

export interface ParsedDataset {
  filename: string;
  headers: string[];
  rows: RawRow[];
  totalRows: number;
}

const MAX_PROTOTYPE_ROWS = 100;

export async function parseDatasetFile(file: File): Promise<ParsedDataset> {
  if (!file) {
    throw new Error('No file was provided');
  }

  const name = file.name.toLowerCase();
  const isCsv = name.endsWith('.csv');
  const isXlsx = name.endsWith('.xlsx') || name.endsWith('.xls');

  if (!isCsv && !isXlsx) {
    throw new Error(
      `Unsupported file type for "${file.name}". Please upload a CSV (.csv) or Excel (.xlsx) file.`
    );
  }

  if (file.size === 0) {
    throw new Error(`File "${file.name}" is empty (0 bytes).`);
  }

  const buffer = await file.arrayBuffer();
  let workbook: XLSX.WorkBook;

  try {
    workbook = XLSX.read(buffer, { type: 'array' });
  } catch {
    throw new Error(`Failed to parse file "${file.name}". Ensure it is a valid CSV or Excel document.`);
  }

  if (!workbook.SheetNames || workbook.SheetNames.length === 0) {
    throw new Error('The uploaded file does not contain any sheets or tabular data.');
  }

  const firstSheetName = workbook.SheetNames[0];
  const worksheet = workbook.Sheets[firstSheetName];
  if (!worksheet) {
    throw new Error('The worksheet is empty or corrupted.');
  }

  // Parse rows with strings
  const rawRows = XLSX.utils.sheet_to_json<Record<string, unknown>>(worksheet, {
    defval: '',
    raw: false,
  });

  if (rawRows.length === 0) {
    throw new Error('The uploaded file contains no data rows.');
  }

  if (rawRows.length > MAX_PROTOTYPE_ROWS) {
    throw new Error(
      `The dataset contains ${rawRows.length} rows. For this prototype, please upload a dataset with 50 or fewer records.`
    );
  }

  // Get distinct headers from all rows to ensure complete columns even if sparse
  const headerSet = new Set<string>();
  rawRows.forEach(row => {
    Object.keys(row).forEach(key => {
      const trimmed = key.trim();
      if (trimmed) headerSet.add(trimmed);
    });
  });

  const headers = Array.from(headerSet);
  if (headers.length === 0) {
    throw new Error('No recognizable column headers were found in the dataset.');
  }

  const rows: RawRow[] = rawRows.map(raw => {
    const rowObj: RawRow = {};
    headers.forEach(h => {
      const val = raw[h];
      rowObj[h] = val !== undefined && val !== null ? String(val).trim() : '';
    });
    return rowObj;
  });

  return {
    filename: file.name,
    headers,
    rows,
    totalRows: rows.length,
  };
}
