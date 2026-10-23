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
  if (sec < -300) {
    return { label: `${Math.round(-sec / 60)}m early`, color: 'error' }
  }
  if (sec > 60) {
    return { label: `${Math.round(sec / 60)}m late`, color: 'warning' }
  }
  if (sec < -60) {
    return { label: `${Math.round(-sec / 60)}m early`, color: 'warning' }
  }
  return { label: 'On time', color: 'success' }
}

export function formatAdherence(sec: number | null | undefined): string {
  if (sec === null || sec === undefined) return 'Unscheduled'

  const totalSeconds = Math.round(Math.abs(sec))
  if (totalSeconds === 0) return 'On time'

  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  const mm = String(minutes).padStart(2, '0')
  const ss = String(seconds).padStart(2, '0')
  return `${sec > 0 ? '+' : '-'}${mm}:${ss}`
}
