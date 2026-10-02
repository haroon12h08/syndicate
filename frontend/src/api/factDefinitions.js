import { apiGet } from './client';

/** The catalogue of things a transaction can state: the vocabulary facts and disclosures share. */
export function listFactDefinitions() {
  return apiGet('/fact-definitions');
}
