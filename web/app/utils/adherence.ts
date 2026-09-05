export interface AdherenceBadge {
  label: string
  color: 'red' | 'amber' | 'green' | 'gray'
}

export function adherenceBadge(sec: number | null | undefined): AdherenceBadge {
  if (sec === null || sec === undefined) {
    return { label: 'Unscheduled', color: 'gray' }
  }
  if (sec > 300) {
    return { label: `${Math.round(sec / 60)}m late`, color: 'red' }
  }
  if (sec < -60) {
    return { label: `${Math.round(-sec / 60)}m early`, color: 'amber' }
  }
  return { label: 'On time', color: 'green' }
}
