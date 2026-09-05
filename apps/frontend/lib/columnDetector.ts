import { ColumnMapping } from '@/lib/api';

function normalize(str: string): string {
  return str.toLowerCase().trim().replace(/[\s\-_]+/g, '_');
}

const PATTERNS = {
  name: [
    'full_name',
    'fullname',
    'person_name',
    'name',
    'contact_name',
    'display_name',
    'target_name',
    'entity_name',
  ],
  url: [
    'linkedin_url',
    'linkedin',
    'profile_url',
    'profile',
    'canonical_url',
    'url',
    'website',
    'target_url',
    'link',
  ],
  organization: [
    'company_name',
    'company',
    'organization_name',
    'organization',
    'org',
    'employer',
    'current_company',
    'current_organization',
  ],
  role: [
    'job_title',
    'title',
    'current_role',
    'role',
    'position',
    'headline',
    'designation',
  ],
  entityType: [
    'entity_type',
    'type',
    'target_type',
    'category',
  ],
};

export function detectColumns(headers: string[]): ColumnMapping {
  const mapping: ColumnMapping = {};
  const normalizedHeaders = headers.map(h => ({ raw: h, norm: normalize(h) }));

  for (const pattern of PATTERNS.name) {
    const found = normalizedHeaders.find(h => h.norm === pattern || h.norm.endsWith('_' + pattern));
    if (found) {
      mapping.nameColumn = found.raw;
      break;
    }
  }

  for (const pattern of PATTERNS.url) {
    const found = normalizedHeaders.find(h => h.norm === pattern || h.norm.includes(pattern));
    if (found) {
      mapping.urlColumn = found.raw;
      break;
    }
  }

  for (const pattern of PATTERNS.organization) {
    const found = normalizedHeaders.find(h => h.norm === pattern || h.norm.includes(pattern));
    if (found) {
      mapping.organizationColumn = found.raw;
      break;
    }
  }

  for (const pattern of PATTERNS.role) {
    const found = normalizedHeaders.find(h => h.norm === pattern || h.norm.includes(pattern));
    if (found) {
      mapping.roleColumn = found.raw;
      break;
    }
  }

  for (const pattern of PATTERNS.entityType) {
    const found = normalizedHeaders.find(h => h.norm === pattern || h.norm.includes(pattern));
    if (found) {
      mapping.entityTypeColumn = found.raw;
      break;
    }
  }

  return mapping;
}
