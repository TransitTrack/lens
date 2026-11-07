export type SplitterOrientation = 'horizontal' | 'vertical'

/**
 * Split-pane pages put their panels side by side from the `lg` breakpoint (1024px) and
 * stack them below it. `isDesktop` is exposed for pages that also size panels per layout.
 */
export function useSplitLayout() {
  const isDesktop = useMediaQuery('(min-width: 1024px)')
  const splitterOrientation = computed<SplitterOrientation>(() =>
    isDesktop.value ? 'horizontal' : 'vertical',
  )
  return { isDesktop, splitterOrientation }
}
