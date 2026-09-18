export type OptimizationDeltaDirection = 'earlier' | 'later' | 'unchanged'

export function formatOptimizationKind(kind: string): string {
  return kind === 'TRIP_SHIFT' ? 'Trip shift' : kind === 'STOP_TIME' ? 'Stop timing' : kind
}

export function formatSignedDuration(seconds: number): {text: string; direction: OptimizationDeltaDirection} {
  if (seconds === 0) return {text: 'No change', direction: 'unchanged'}

  const absolute = Math.abs(seconds)
  const minutes = Math.floor(absolute / 60)
  const remainder = absolute % 60
  const duration = minutes ? `${minutes}m${remainder ? ` ${remainder}s` : ''}` : `${remainder}s`

  return {
    text: `${seconds > 0 ? '+' : '−'}${duration}`,
    direction: seconds > 0 ? 'later' : 'earlier',
  }
}
