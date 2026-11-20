<template>
  <span class="ability-text">
    <span
      v-for="(seg, i) in segments"
      :key="i"
      :class="seg.part ? `part ${seg.part}` : undefined"
      :title="seg.part ? PART_LABELS[seg.part] : undefined"
    ><mark v-if="seg.severity" :class="[seg.severity, { reviewed: seg.reviewed }]">{{ seg.text }}</mark><template v-else>{{ seg.text }}</template></span>
  </span>
</template>

<script setup lang="ts">
import { PART_LABELS, type TextSegment } from '../../analysis'

/** Text split into segments: ability parts are colored, rule matches highlighted. */
defineProps<{ segments: TextSegment[] }>()
</script>

<style scoped>
.ability-text { white-space: pre-wrap; }
/* Parts of an ability: the trigger and the condition stand out from the effect. */
.part.trigger { color: var(--part-trigger); font-weight: 600; }
.part.condition { color: var(--part-condition); font-style: italic; }

mark {
  color: #fff;
  border-radius: 3px;
  padding: 0 0.1rem;
}

mark.HIGH { background: #8a2430; }
mark.MEDIUM { background: #7a5410; }
mark.LOW { background: #1d4e7a; }
mark.reviewed { background: #24402f; color: #cfe; }
</style>
