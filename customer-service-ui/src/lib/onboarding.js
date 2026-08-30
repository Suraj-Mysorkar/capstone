// Mirrors the customer-service backend:
//  - entity/OnboardingStatus.java
//  - service/OnboardingStatusTransitionValidator.java
// Keep this in sync with those files.

export const ONBOARDING_STATUSES = [
  'REGISTERED',
  'DOCUMENTS_PENDING',
  'DOCUMENTS_SUBMITTED',
  'KYC_IN_REVIEW',
  'KYC_APPROVED',
  'KYC_REJECTED',
  'ONBOARDING_COMPLETE',
  'SUSPENDED',
];

/** Legal next states for each onboarding status (same rules as the backend validator). */
export const ALLOWED_TRANSITIONS = {
  REGISTERED:          ['DOCUMENTS_PENDING', 'SUSPENDED'],
  DOCUMENTS_PENDING:   ['DOCUMENTS_SUBMITTED', 'SUSPENDED'],
  DOCUMENTS_SUBMITTED: ['KYC_IN_REVIEW', 'DOCUMENTS_PENDING', 'SUSPENDED'],
  KYC_IN_REVIEW:       ['KYC_APPROVED', 'KYC_REJECTED', 'SUSPENDED'],
  KYC_APPROVED:        ['ONBOARDING_COMPLETE', 'SUSPENDED'],
  KYC_REJECTED:        ['DOCUMENTS_PENDING', 'SUSPENDED'],
  ONBOARDING_COMPLETE: ['SUSPENDED'],
  SUSPENDED:           ['DOCUMENTS_PENDING', 'KYC_IN_REVIEW'],
};

export const STATUS_LABEL = {
  REGISTERED:          'Registered',
  DOCUMENTS_PENDING:   'Documents Pending',
  DOCUMENTS_SUBMITTED: 'Documents Submitted',
  KYC_IN_REVIEW:       'KYC In Review',
  KYC_APPROVED:        'KYC Approved',
  KYC_REJECTED:        'KYC Rejected',
  ONBOARDING_COMPLETE: 'Onboarding Complete',
  SUSPENDED:           'Suspended',
};

export function prettyStatus(s) {
  return STATUS_LABEL[s] || (s ? s.replace(/_/g, ' ') : '—');
}

/** Maps an onboarding status onto one of index.css's badge classes. */
export function statusBadgeClass(s) {
  switch (s) {
    case 'KYC_APPROVED':
    case 'ONBOARDING_COMPLETE':
      return 'badge-approved';
    case 'KYC_REJECTED':
    case 'SUSPENDED':
      return 'badge-rejected';
    case 'KYC_IN_REVIEW':
    case 'DOCUMENTS_SUBMITTED':
      return 'badge-review';
    default:
      return 'badge-default';
  }
}

/** The "happy path" order used for the lifecycle strip on the detail page. */
export const HAPPY_PATH = [
  'REGISTERED',
  'DOCUMENTS_PENDING',
  'DOCUMENTS_SUBMITTED',
  'KYC_IN_REVIEW',
  'KYC_APPROVED',
  'ONBOARDING_COMPLETE',
];

export const ISO_COUNTRIES = ['US', 'IN', 'GB', 'CA', 'AU', 'DE', 'FR', 'SG', 'AE', 'JP'];
