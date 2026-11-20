<template>
  <Modal title="Dangerous effect rules" size="large" @close="$emit('close')">
    <div class="toolbar">
      <input type="text" v-model="search" placeholder="Search rules…" class="search" />
      <select v-model="category" class="cat">
        <option value="">All categories</option>
        <option v-for="c in categories" :key="c" :value="c">{{ c }}</option>
      </select>
      <button type="button" class="btn primary" @click="editing = { rule: null }">+ New rule</button>
    </div>

    <p v-if="error" class="error-box">{{ error }}</p>

    <p v-if="!filtered.length" class="empty">No rules.</p>

    <table v-else class="rules">
      <thead>
        <tr>
          <th>On</th>
          <th>Severity</th>
          <th>Rule</th>
          <th class="num">In pool</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="r in filtered" :key="r.id" :class="{ off: !r.enabled }">
          <td>
            <input type="checkbox" :checked="r.enabled" :disabled="busy === r.id" @change="toggle(r)" :aria-label="`Enable ${r.label}`" />
          </td>
          <td><span class="sev" :class="r.severity">{{ r.severity }}</span></td>
          <td>
            <div class="label">
              {{ r.label }}
              <span v-if="r.category" class="cat-tag">{{ r.category }}</span>
              <span v-for="f in r.factions" :key="f" class="cat-tag faction">{{ factionName(f) }}</span>
            </div>
            <div class="conds">
              <span v-for="(c, i) in r.conditions" :key="i">{{ i ? ' and ' : '' }}{{ describeCondition(c) }}</span>
            </div>
            <div v-if="r.reason" class="reason">{{ r.reason }}</div>
          </td>
          <td class="num">{{ r.enabled ? (hitCounts[r.id!] ?? 0) : '–' }}</td>
          <td class="actions">
            <button type="button" class="btn small" @click="editing = { rule: r }">Edit</button>
            <button type="button" class="btn small danger" :disabled="busy === r.id" @click="remove(r)">Delete</button>
          </td>
        </tr>
      </tbody>
    </table>

    <template #footer>
      <span class="muted footnote">Saved in <code>backend/data/rules.json</code>: kept for every future pool.</span>
      <button type="button" class="btn" @click="$emit('close')">Close</button>
    </template>
  </Modal>

  <RuleEditor
    v-if="editing"
    :rule="editing.rule"
    :pool-cards="poolCards"
    :categories="categories"
    @close="editing = null"
    @saved="onSaved"
  />
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import Modal from '../Modal.vue'
import RuleEditor from './RuleEditor.vue'
import { api, errorMessage } from '../../api'
import {
  describeCondition,
  factionName,
  SEVERITY_RANK,
  type EffectRule,
  type PoolCard,
} from '../../analysis'

const props = defineProps<{
  rules: EffectRule[]
  hitCounts: Record<string, number>
  poolCards: PoolCard[]
  categories: string[]
}>()
const emit = defineEmits<{ close: []; changed: [] }>()

const search = ref('')
const category = ref('')
const busy = ref<string | null>(null)
const error = ref('')
const editing = ref<{ rule: EffectRule | null } | null>(null)

const filtered = computed(() => {
  const q = search.value.trim().toLowerCase()
  return props.rules
    .filter((r) => !category.value || r.category === category.value)
    .filter(
      (r) =>
        !q ||
        r.label.toLowerCase().includes(q) ||
        (r.reason ?? '').toLowerCase().includes(q) ||
        r.conditions.some((c) => c.value.toLowerCase().includes(q))
    )
    .sort(
      (a, b) =>
        Number(b.enabled) - Number(a.enabled) ||
        SEVERITY_RANK[b.severity] - SEVERITY_RANK[a.severity] ||
        a.label.localeCompare(b.label)
    )
})

const toggle = async (rule: EffectRule) => {
  busy.value = rule.id!
  error.value = ''
  try {
    await api.updateRule(rule.id!, { ...rule, enabled: !rule.enabled })
    emit('changed')
  } catch (err) {
    error.value = errorMessage(err)
  } finally {
    busy.value = null
  }
}

const remove = async (rule: EffectRule) => {
  if (!confirm(`Delete the rule “${rule.label}”? Cards it flags will no longer be reported.`)) return
  busy.value = rule.id!
  error.value = ''
  try {
    await api.deleteRule(rule.id!)
    emit('changed')
  } catch (err) {
    error.value = errorMessage(err)
  } finally {
    busy.value = null
  }
}

const onSaved = () => {
  editing.value = null
  emit('changed')
}
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1rem;
  flex-wrap: wrap;
}

.search { flex: 1; min-width: 200px; padding: 0.45rem 0.6rem; font-size: 0.85rem; }
.cat { width: auto; padding: 0.45rem 0.6rem; font-size: 0.85rem; }
.empty { text-align: center; color: var(--text-dim); padding: 2rem 0; }
.footnote { margin-right: auto; align-self: center; }
.footnote code { color: #aaa; }

.rules {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.85rem;
}

.rules th {
  text-align: left;
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: #777;
  padding: 0.4rem;
  border-bottom: 1px solid var(--border);
}

.rules td {
  padding: 0.55rem 0.4rem;
  border-bottom: 1px solid #222;
  vertical-align: top;
  color: #ddd;
}

.rules tr.off td { opacity: 0.45; }
.rules input[type='checkbox'] { accent-color: var(--accent); }
.num { text-align: right; white-space: nowrap; }
.actions { white-space: nowrap; text-align: right; }
.actions .btn + .btn { margin-left: 0.3rem; }

.label { font-weight: 600; }

.cat-tag {
  font-size: 0.68rem;
  font-weight: 500;
  color: #9ab;
  background: #1f2a30;
  border-radius: 4px;
  padding: 0.05rem 0.35rem;
  margin-left: 0.35rem;
}

.cat-tag.faction { color: #c3a8ff; background: #2a2438; }

.conds {
  font-size: 0.75rem;
  color: #999;
  margin-top: 0.15rem;
  overflow-wrap: anywhere;
}

.reason {
  font-size: 0.75rem;
  color: var(--reason);
  margin-top: 0.15rem;
}
</style>
