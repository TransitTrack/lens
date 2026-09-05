export interface AdherenceBadge {
  label: string
  color: 'error' | 'warning' | 'success' | 'neutral'
}

export function adherenceBadge(sec: number | null | undefined): AdherenceBadge {
  if (sec === null || sec === undefined) {
    return { label: 'Unscheduled', color: 'neutral' }
  }
  if (sec > 300) {
    return { label: `${Math.round(sec / 60)}m late`, color: 'error' }
  }
  if (sec < -60) {
    return { label: `${Math.round(-sec / 60)}m early`, color: 'warning' }
  }
  return { label: 'On time', color: 'success' }
}
