export interface AdherenceBadge {
  label: string
  color: 'error' | 'warning' | 'success' | 'neutral'
}

type Translate = (key: string, params?: Record<string, unknown>) => string

/** Colours/thresholds are language-independent; only the label is translated through `t`. */
export function adherenceBadge(sec: number | null | undefined, t: Translate): AdherenceBadge {
  if (sec === null || sec === undefined) {
    return { label: t('reports.adherence.unscheduled'), color: 'neutral' }
  }
  if (sec > 300) {
    return { label: t('reports.adherence.minutesLate', { n: Math.round(sec / 60) }), color: 'error' }
  }
  if (sec < -300) {
    return { label: t('reports.adherence.minutesEarly', { n: Math.round(-sec / 60) }), color: 'error' }
  }
  if (sec > 60) {
    return { label: t('reports.adherence.minutesLate', { n: Math.round(sec / 60) }), color: 'warning' }
  }
  if (sec < -60) {
    return { label: t('reports.adherence.minutesEarly', { n: Math.round(-sec / 60) }), color: 'warning' }
  }
  return { label: t('reports.adherence.onTime'), color: 'success' }
}

export function formatAdherence(sec: number | null | undefined, t: Translate): string {
  if (sec === null || sec === undefined) return t('reports.adherence.unscheduled')

  const totalSeconds = Math.round(Math.abs(sec))
  if (totalSeconds === 0) return t('reports.adherence.onTime')

  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  const mm = String(minutes).padStart(2, '0')
  const ss = String(seconds).padStart(2, '0')
  return `${sec > 0 ? '+' : '-'}${mm}:${ss}`
}

/** Always-numeric `+mm:ss`/`-mm:ss`, including at zero - unlike `formatAdherence`, which reads
 * better as "On time" in a badge/list context, this is for a dense table column where a
 * consistent, scannable value matters more than a worded special case. */
export function signedAdherence(sec: number): string {
  const totalSeconds = Math.round(Math.abs(sec))
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  const mm = String(minutes).padStart(2, '0')
  const ss = String(seconds).padStart(2, '0')
  return `${sec >= 0 ? '+' : '-'}${mm}:${ss}`
}
