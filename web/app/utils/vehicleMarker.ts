export function colorForVehicle(matched: boolean, stale: boolean): string {
  if (stale) return '#f59e0b'
  if (matched) return '#22c55e'
  return '#9ca3af'
}
