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

  // Create clean export rows with prioritized high-signal columns first
  const exportRows = records.map((record) => {
    const row: Record<string, string | number> = {};

    const profile = record.profile;
    const assessment = record.assessment;
    const recommendation = record.recommendation;

    // 1. High-Signal Objective & Profile Columns First
    row['Name'] = record.displayName || record.originalData['name'] || 'Unknown';
    row['Current_Role'] = profile?.currentRole || record.attributes?.currentRole?.value || 'UNKNOWN';
    row['Current_Organization'] = profile?.currentOrganization || record.attributes?.currentOrganization?.value || 'UNKNOWN';
    row['Location'] = profile?.location || record.attributes?.location?.value || 'UNKNOWN';
    row['Relevance_Score'] = assessment?.overallScore ?? Math.round((record.confidence || 0) * 100);
    row['Priority_Tier'] = assessment?.priorityTier || (record.confidence && record.confidence > 0.7 ? 'HIGH' : 'MEDIUM');
    row['Why_Relevant'] = assessment?.whyRelevant || 'Evaluated against research criteria.';
    row['Professional_Summary'] = profile?.professionalSummary || record.attributes?.summary?.value || '';
    row['Key_Expertise'] = profile?.technicalExpertise?.length
      ? profile.technicalExpertise.join(', ')
      : record.attributes?.skills?.value || '';
    row['Recommended_Approach'] = recommendation?.approachType
      ? `${recommendation.approachType}: ${recommendation.summary}`
      : 'Professional networking outreach';
    row['Top_Sources'] = record.sources && record.sources.length > 0
      ? record.sources.map((s) => s.url).slice(0, 3).join(' | ')
      : record.canonicalUrl || '';
    row['Enrichment_Status'] = record.status;

    // 2. Canonical Profile URL
    const canonicalUrl = record.canonicalUrl || record.response?.result?.canonicalUrl;
    if (canonicalUrl) {
      row['Canonical_Url'] = canonicalUrl;
    }

    // 3. Original Dataset Columns
    if (record.originalData) {
      for (const [key, val] of Object.entries(record.originalData)) {
        if (row[key] === undefined) {
          row[`Original_${key}`] = val;
        }
      }
    }

    // 4. Additional Extracted Attributes
    if (record.attributes) {
      for (const [key, attr] of Object.entries(record.attributes)) {
        if (
          key !== 'currentRole' &&
          key !== 'currentOrganization' &&
          key !== 'location' &&
          key !== 'skills' &&
          key !== 'summary'
        ) {
          row[`Enriched_${key}`] = attr.value ?? 'UNKNOWN';
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
