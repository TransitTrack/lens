/** `true` iff both dates are `YYYY-MM-DD` and `endDate >= startDate`. */
export function isValidAdherenceRange(startDate: string, endDate: string): boolean {
  return /^\d{4}-\d{2}-\d{2}$/.test(startDate) && /^\d{4}-\d{2}-\d{2}$/.test(endDate) && startDate <= endDate
}
