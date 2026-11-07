<script setup lang="ts">
import { useValidationFindingsQuery } from '~~/generated/graphql'

/**
 * GTFS analyzer findings for one entity (trip, stop, route, ...), straight from the feed's active
 * revision, as compact header buttons (data quality / analytics) that open their findings in a
 * popover. Renders nothing while loading, on error, or when the entity has no findings.
 */
const props = defineProps<{
  feedCode: string | null | undefined
  entityType: 'Trip' | 'Stop' | 'Route' | 'Shape' | 'Service' | 'Agency'
  entityId: string
}>()

const { t } = useI18n()
const PREVIEW = 5
// i18n-keys: findings.severity.*

const { result } = useValidationFindingsQuery(() => ({
  variables: { feedCode: props.feedCode ?? '', entityType: props.entityType, entityId: props.entityId },
  enabled: !!props.feedCode && !!props.entityId,
}))

const findings = computed(() => result.value?.validationFindings ?? [])
type Finding = (typeof findings.value)[number]

// Analytics rules (e.g. excessive speed) are heuristics rather than data defects, so they get their
// own button. Each button opens its list in a popover, so nothing is shown until asked for.
const sections = computed(() => [
  { key: 'quality', items: findings.value.filter((f) => f.ruleClass !== 'ANALYTICS') },
  { key: 'analytics', items: findings.value.filter((f) => f.ruleClass === 'ANALYTICS') },
])
const showAll = reactive<Record<string, boolean>>({})

function visible(section: { key: string; items: Finding[] }): Finding[] {
  return showAll[section.key] ? section.items : section.items.slice(0, PREVIEW)
}

function severityColor(severity: Finding['severity']): 'error' | 'warning' | 'info' | 'neutral' {
  if (severity === 'CRITICAL' || severity === 'HIGH') return 'error'
  if (severity === 'MEDIUM') return 'warning'
  if (severity === 'LOW') return 'neutral'
  return 'info'
}

function where(finding: Finding): string | null {
  if (!finding.file) return null
  return finding.line == null
    ? finding.file
    : t('findings.location', { file: finding.file, line: finding.line })
}

// The observed/expected pair is only worth a row when the rule filled it in.
function hasValues(finding: Finding): boolean {
  return !!finding.observedValue || !!finding.expectedValue
}
</script>

<template>
  <div v-if="findings.length" class="flex items-center gap-2" data-testid="validation-findings">
    <template v-for="section in sections" :key="section.key">
      <UPopover v-if="section.items.length" :content="{ align: 'end' }">
        <UButton
          color="neutral"
          variant="subtle"
          size="sm"
          :icon="section.key === 'analytics' ? 'i-lucide-activity' : 'i-lucide-shield-alert'"
          :data-testid="`findings-${section.key}`"
        >
          {{ section.key === 'analytics' ? $t('findings.analyticsTitle') : $t('findings.qualityShort') }}
          <UBadge :color="severityColor(section.items[0]!.severity)" variant="subtle" size="sm">
            {{ section.items.length }}
          </UBadge>
        </UButton>

        <template #content>
          <div class="max-h-[70vh] w-[28rem] max-w-[calc(100vw-2rem)] overflow-y-auto">
            <div class="border-b border-default px-3 py-2.5 text-xs font-semibold uppercase tracking-wide text-dimmed">
              {{ section.key === 'analytics' ? $t('findings.analyticsTitle') : $t('findings.title') }}
            </div>
            <ul class="divide-y divide-default">
              <li
                v-for="(finding, index) in visible(section)"
                :key="`${finding.ruleId}:${finding.line ?? index}`"
                class="space-y-1.5 p-3"
              >
                <div class="flex flex-wrap items-center gap-1.5">
                  <UBadge :color="severityColor(finding.severity)" variant="subtle" size="sm">
                    {{ $t(`findings.severity.${finding.severity}`) }}
                  </UBadge>
                  <code class="text-xs text-muted">{{ finding.ruleId }}</code>
                  <span v-if="where(finding)" class="ml-auto text-xs tabular-nums text-dimmed">{{ where(finding) }}</span>
                </div>
                <p class="text-sm font-medium text-highlighted">{{ finding.title }}</p>
                <p class="text-sm text-muted">{{ finding.message }}</p>
                <dl v-if="hasValues(finding)" class="grid grid-cols-[auto_1fr] gap-x-3 gap-y-0.5 text-xs">
                  <template v-if="finding.observedValue">
                    <dt class="text-dimmed">{{ $t('findings.observed') }}</dt>
                    <dd class="tabular-nums text-highlighted">{{ finding.observedValue }}</dd>
                  </template>
                  <template v-if="finding.expectedValue">
                    <dt class="text-dimmed">{{ $t('findings.expected') }}</dt>
                    <dd class="tabular-nums text-highlighted">{{ finding.expectedValue }}</dd>
                  </template>
                </dl>
                <p v-if="finding.remediation" class="text-xs text-muted">
                  <span class="font-semibold text-default">{{ $t('findings.whatToDo') }}:</span>
                  {{ finding.remediation }}
                </p>
              </li>
            </ul>
            <div v-if="section.items.length > PREVIEW" class="border-t border-default p-2">
              <UButton
                color="neutral"
                variant="ghost"
                size="xs"
                block
                :label="showAll[section.key] ? $t('findings.showLess') : $t('findings.showAll', { count: section.items.length })"
                @click="showAll[section.key] = !showAll[section.key]"
              />
            </div>
          </div>
        </template>
      </UPopover>
    </template>
  </div>
</template>
