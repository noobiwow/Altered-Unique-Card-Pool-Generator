<template>
  <main class="report-page" :class="{ paper: paper || exporting, exporting }">
    <div v-if="exporting" class="export-overlay no-print" role="status">
      <div class="spinner"></div>
      <p>Building the PDF… {{ progress.done }} / {{ progress.total }}</p>
    </div>

    <div v-if="loading" class="loading">
      <div class="spinner"></div>
      <p>Loading report…</p>
    </div>

    <p v-else-if="error" class="error-box">{{ error }}</p>

    <article v-else-if="report" ref="reportEl" class="report">
      <header class="report-header" data-pdf-block>
        <div>
          <p class="eyebrow">Pool analysis report</p>
          <h1>{{ report.title }}</h1>
          <p class="meta">
            <span v-if="report.poolName">{{ report.poolName }} · </span>{{ report.poolSize }} cards analysed ·
            {{ formatDate(report.createdAt) }}
          </p>
        </div>
        <div class="actions no-print">
          <button type="button" class="btn" @click="copyLink">{{ copied ? 'Link copied ✓' : 'Copy link' }}</button>
          <button type="button" class="btn" @click="print">Print</button>
          <button type="button" class="btn primary" :disabled="exporting" @click="downloadPdf">Download PDF</button>
        </div>
      </header>

      <p v-if="report.notes" class="notes" data-pdf-block>{{ report.notes }}</p>

      <section class="summary" data-pdf-block>
        <div class="stat">
          <span class="num flagged">{{ report.summary.flagged }}</span>
          <span>to review</span>
        </div>
        <div class="stat">
          <span class="num rejected">{{ report.summary.rejected }}</span>
          <span>rejected</span>
        </div>
        <div v-if="report.summary.cleared" class="stat">
          <span class="num cleared">{{ report.summary.cleared }}</span>
          <span>approved</span>
        </div>
        <div class="stat sev-stats">
          <span v-for="s in SEVERITIES" :key="s" class="sev-line">
            <span class="sev" :class="s">{{ s }}</span> {{ report.summary.bySeverity[s] ?? 0 }}
          </span>
        </div>
        <div class="stat factions">
          <span v-for="(n, code) in report.summary.byFaction" :key="code">{{ factionName(code) }} {{ n }}</span>
        </div>
      </section>

      <div class="toolbar no-print">
        <label v-for="s in statuses" :key="s" class="toggle">
          <input type="checkbox" v-model="shown[s]" />
          {{ STATUS_LABELS[s] }} ({{ countBy(s) }})
        </label>
        <label class="toggle">
          <input type="checkbox" v-model="showText" />
          Card text
        </label>
        <label class="toggle">
          <input type="checkbox" v-model="showDecisionBox" />
          Decision boxes
        </label>
      </div>

      <p v-if="exportError" class="error-box no-print">{{ exportError }}</p>

      <p v-if="!visibleCards.length" class="empty">No cards to show.</p>

      <section v-for="group in groups" :key="group.status" class="status-group">
        <h2 data-pdf-block data-pdf-keep-with-next>
          <span class="status" :class="group.status">{{ STATUS_LABELS[group.status] }}</span>
          <span class="group-count">{{ group.cards.length }} card{{ group.cards.length === 1 ? '' : 's' }}</span>
        </h2>

        <div v-for="card in group.cards" :key="card.reference" class="report-card" data-pdf-block>
          <div class="details">
            <div class="title-row">
              <h3>{{ card.name || card.reference }}</h3>
              <span v-if="topSeverity(card)" class="sev" :class="topSeverity(card)">{{ topSeverity(card) }}</span>
            </div>
            <p class="facts">
              <span>{{ card.factionName || factionName(card.factionCode) }}</span>
              <span v-if="card.setName">{{ card.setName }}</span>
              <span v-if="card.cardType">{{ card.cardType }}</span>
              <span v-if="card.handCost || card.reserveCost">Cost {{ card.handCost ?? '?' }}/{{ card.reserveCost ?? '?' }}</span>
              <span class="ref">{{ card.reference }}</span>
            </p>

            <CardText
              v-if="showText"
              :card="card"
              :outlined="card.warnings.filter((w) => !w.reviewed).map((w) => w.ability)"
              class="card-text"
            />

            <ul class="warnings">
              <li v-for="w in card.warnings" :key="w.ruleId" :class="{ reviewed: w.reviewed }">
                <div class="w-head">
                  <span class="sev" :class="w.severity">{{ w.severity }}</span>
                  <strong>{{ w.label }}</strong>
                  <span v-if="w.category" class="cat">{{ w.category }}</span>
                  <span v-if="w.reviewed" class="reviewed-tag">already reviewed</span>
                </div>
                <p v-if="w.reason" class="reason">{{ w.reason }}</p>
                <dl v-if="w.ability" class="combo">
                  <template v-for="part in ABILITY_PARTS" :key="part">
                    <dt :class="part">{{ PART_LABELS[part] }}</dt>
                    <dd :class="{ none: !w.ability[part] }">{{ w.ability[part] || '—' }}</dd>
                  </template>
                </dl>
                <p v-else-if="w.matchedText && w.source !== 'NAME'" class="match">
                  <span class="src">{{ w.source === 'ECHO' ? 'Echo' : 'Main' }}</span>{{ w.matchedText }}
                </p>
              </li>
              <li v-if="!card.warnings.length" class="no-warning">No rule matches: rejected manually.</li>
            </ul>

            <p v-if="card.verdict" class="verdict">
              <strong>{{ card.verdict.status === 'APPROVED' ? 'Approved' : 'Rejected' }}</strong>
              on {{ formatDate(card.verdict.decidedAt) }}<span v-if="card.verdict.note">: {{ card.verdict.note }}</span>
            </p>

            <div v-if="showDecisionBox" class="decision">
              <span class="box">☐ Keep</span>
              <span class="box">☐ Reject</span>
              <span class="box">☐ Discuss</span>
              <span class="lines">Notes:</span>
            </div>
          </div>
        </div>
      </section>

      <section v-if="report.replacements.length" class="replacements" data-pdf-block>
        <h2>Replacements made before this report</h2>
        <table>
          <thead>
            <tr><th>Removed</th><th>Added</th><th>When</th></tr>
          </thead>
          <tbody>
            <tr v-for="r in report.replacements" :key="r.fromReference + r.toReference">
              <td>{{ r.fromName || r.fromReference }} <span class="ref">{{ r.fromReference }}</span></td>
              <td>{{ r.toName || r.toReference }} <span class="ref">{{ r.toReference }}</span></td>
              <td>{{ formatDate(r.replacedAt) }}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <footer class="report-footer" data-pdf-block>Report {{ report.id }}</footer>
    </article>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import CardText from '../components/analysis/CardText.vue'
import { api, errorMessage } from '../api'
import { downloadReportPdf } from '../report-pdf'
import {
  factionName,
  ABILITY_PARTS,
  PART_LABELS,
  SEVERITIES,
  SEVERITY_RANK,
  STATUS_LABELS,
  type AnalysisStatus,
  type Report,
  type ReportCard,
  topSeverity,
} from '../analysis'

const route = useRoute()
const report = ref<Report | null>(null)
const loading = ref(true)
const error = ref('')
const copied = ref(false)
const showText = ref(true)
const showDecisionBox = ref(true)

const statuses: AnalysisStatus[] = ['FLAGGED', 'REJECTED', 'CLEARED']
const shown = reactive<Record<AnalysisStatus, boolean>>({ FLAGGED: true, REJECTED: true, CLEARED: true })

const severityRank = (c: ReportCard) => {
  const severity = topSeverity(c)
  return severity ? SEVERITY_RANK[severity] : 0
}
const countBy = (s: AnalysisStatus) => report.value?.cards.filter((c) => c.status === s).length ?? 0

const visibleCards = computed(() => (report.value?.cards ?? []).filter((c) => shown[c.status]))

const groups = computed(() =>
  statuses
    .map((status) => ({
      status,
      cards: visibleCards.value
        .filter((c) => c.status === status)
        .sort(
          (a, b) =>
            severityRank(b) - severityRank(a) ||
            (a.factionCode ?? '').localeCompare(b.factionCode ?? '') ||
            (a.name ?? '').localeCompare(b.name ?? '')
        ),
    }))
    .filter((g) => g.cards.length)
)

const formatDate = (iso?: string) => (iso ? new Date(iso).toLocaleString() : '')

const copyLink = async () => {
  try {
    await navigator.clipboard.writeText(window.location.href)
    copied.value = true
    setTimeout(() => (copied.value = false), 2000)
  } catch {
    prompt('Copy this link:', window.location.href)
  }
}

/** Light "paper" styling, used when printing and to build the PDF. */
const paper = ref(false)
const exporting = ref(false)
const exportError = ref('')
const progress = reactive({ done: 0, total: 0 })
const reportEl = ref<HTMLElement | null>(null)

const print = () => window.print()
const beforePrint = () => (paper.value = true)
const afterPrint = () => (paper.value = false)

const fileName = () =>
  `${(report.value?.title || 'report').replace(/[\\/:*?"<>|]+/g, '-').replace(/\s+/g, ' ').trim()}.pdf`

const downloadPdf = async () => {
  if (exporting.value) return
  exportError.value = ''
  progress.done = 0
  progress.total = 0
  exporting.value = true
  try {
    await nextTick()
    // Let the browser lay the paper styling out before measuring it.
    await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)))
    if (!reportEl.value) throw new Error('The report is not displayed.')
    await downloadReportPdf(reportEl.value, fileName(), (done, total) => {
      progress.done = done
      progress.total = total
    })
  } catch (err) {
    exportError.value = `Could not build the PDF: ${errorMessage(err)}`
  } finally {
    exporting.value = false
  }
}

onBeforeUnmount(() => {
  window.removeEventListener('beforeprint', beforePrint)
  window.removeEventListener('afterprint', afterPrint)
})

onMounted(async () => {
  window.addEventListener('beforeprint', beforePrint)
  window.addEventListener('afterprint', afterPrint)
  try {
    report.value = await api.getReport(String(route.params.id))
    document.title = `${report.value.title} · Card Pool Generator`
  } catch (err) {
    error.value = (err as any)?.response?.status === 404 ? 'This report does not exist (anymore).' : errorMessage(err)
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.report-page {
  background: var(--bg);
  padding: 4rem 1.5rem 3rem;
}

.report {
  max-width: 1000px;
  margin: 0 auto;
}

.report-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
  flex-wrap: wrap;
  margin-bottom: 1rem;
}

.eyebrow {
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--accent);
}

.report-header h1 {
  text-align: left;
  margin: 0.2rem 0;
  font-size: 1.7rem;
}

.meta { color: var(--text-muted); font-size: 0.85rem; }
.actions { display: flex; gap: 0.5rem; }

.notes {
  white-space: pre-wrap;
  background: var(--surface-raised);
  border-left: 3px solid var(--accent);
  padding: 0.7rem 0.9rem;
  border-radius: 4px;
  color: #ccc;
  font-size: 0.9rem;
  margin-bottom: 1rem;
}

.summary {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  margin-bottom: 1rem;
}

.stat {
  display: flex;
  flex-direction: column;
  justify-content: center;
  background: var(--surface-raised);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0.6rem 1rem;
  font-size: 0.8rem;
  color: #999;
  min-width: 90px;
}

.stat .num { font-size: 1.6rem; font-weight: 700; color: #fff; line-height: 1.1; }
.stat .num.flagged { color: var(--warning); }
.stat .num.rejected { color: var(--danger); }
.stat .num.cleared { color: var(--success); }
.sev-stats, .factions { gap: 0.2rem; }
.sev-line { display: flex; align-items: center; gap: 0.4rem; color: #ddd; }
.factions span { color: #bbb; }

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem 1.2rem;
  margin-bottom: 1.5rem;
  padding: 0.6rem 0.8rem;
  background: var(--surface-sunken);
  border-radius: 8px;
}

.toggle {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  margin: 0;
  text-transform: none;
  letter-spacing: 0;
  font-size: 0.85rem;
  color: #ccc;
  cursor: pointer;
}

.toggle input { accent-color: var(--accent); }
.empty { text-align: center; color: var(--text-dim); padding: 2rem; }

.status-group { margin-bottom: 2rem; }

.status-group h2 {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  font-size: 1rem;
  margin-bottom: 0.8rem;
  padding-bottom: 0.4rem;
  border-bottom: 1px solid var(--border);
}

.status-group h2 .status { font-size: 0.85rem; }
.group-count { font-size: 0.85rem; color: var(--text-muted); font-weight: 400; }

.report-card {
  padding: 1rem;
  background: #171717;
  border: 1px solid #262626;
  border-radius: 10px;
  margin-bottom: 0.9rem;
  break-inside: avoid;
  page-break-inside: avoid;
}

.card-text { margin: 0.6rem 0 0.8rem; }

.title-row { display: flex; align-items: center; gap: 0.5rem; }
.title-row h3 { color: #fff; font-size: 1.1rem; }

.facts {
  display: flex;
  flex-wrap: wrap;
  gap: 0.2rem 0.8rem;
  font-size: 0.8rem;
  color: #999;
  margin: 0.2rem 0 0.7rem;
}

.ref { font-family: monospace; font-size: 0.72rem; color: #777; overflow-wrap: anywhere; }

.warnings {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

.warnings li {
  border-left: 3px solid #5a3a2a;
  padding: 0.2rem 0 0.2rem 0.6rem;
}

.warnings li.reviewed { opacity: 0.6; border-left-color: #2a4a3a; }
.w-head { display: flex; align-items: center; flex-wrap: wrap; gap: 0.4rem; font-size: 0.88rem; color: #eee; }
.cat { font-size: 0.72rem; color: #9ab; }
.reviewed-tag { font-size: 0.72rem; color: var(--success); }
.reason { font-size: 0.82rem; color: var(--reason); margin-top: 0.15rem; }
.match { font-size: 0.8rem; color: #aaa; margin-top: 0.15rem; font-style: italic; }
.match .src { font-style: normal; font-size: 0.65rem; font-weight: 700; text-transform: uppercase; color: #8fdcff; margin-right: 0.35rem; }
.combo {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.1rem 0.6rem;
  margin-top: 0.25rem;
  font-size: 0.8rem;
}
.combo dt { font-size: 0.65rem; font-weight: 700; text-transform: uppercase; padding-top: 0.15rem; }
.combo dt.trigger { color: var(--part-trigger); }
.combo dt.condition { color: var(--part-condition); }
.combo dt.effect { color: var(--text-muted); }
.combo dd { color: #ccc; }
.combo dd.none { color: var(--text-dim); }
.no-warning { color: var(--text-muted); font-size: 0.82rem; border-left-color: #333 !important; }

.verdict {
  margin-top: 0.7rem;
  font-size: 0.82rem;
  color: #bbb;
  background: #1d1d1d;
  border-radius: 6px;
  padding: 0.4rem 0.6rem;
}

.decision {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 1rem;
  margin-top: 0.9rem;
  padding-top: 0.6rem;
  border-top: 1px dashed #333;
  font-size: 0.85rem;
  color: #aaa;
}

.decision .lines {
  flex: 1;
  min-width: 200px;
  border-bottom: 1px solid #444;
  padding-bottom: 0.1rem;
}

.replacements h2 { font-size: 1rem; margin-bottom: 0.6rem; }
.replacements table { width: 100%; border-collapse: collapse; font-size: 0.85rem; }
.replacements th { text-align: left; color: #777; font-size: 0.72rem; text-transform: uppercase; padding: 0.35rem; border-bottom: 1px solid var(--border); }
.replacements td { padding: 0.45rem 0.35rem; border-bottom: 1px solid #222; color: #ddd; }

.report-footer { margin-top: 2rem; text-align: center; color: #555; font-size: 0.75rem; }

/* Paper styling: printing and the PDF export. */
.report-page.paper { background: #fff; }
.report-page.exporting .report { width: 760px; max-width: none; }
.paper .no-print { display: none !important; }
.paper .combo dd { color: #222 !important; }
.paper .combo dt.trigger { color: #0b5d86 !important; }
.paper .combo dt.condition { color: #7a2c96 !important; }
.paper .combo dt.effect { color: #555 !important; }
.paper .eyebrow { color: #0b5d86 !important; }
.paper .report-header h1, .paper .title-row h3, .paper .w-head, .paper .stat .num, .paper .status-group h2 { color: #000 !important; }
.paper .meta, .paper .facts, .paper .group-count, .paper .stat, .paper .ref, .paper .match, .paper .decision, .paper .report-footer { color: #444 !important; }
.paper .stat .num.flagged { color: #a86a00 !important; }
.paper .stat .num.rejected { color: #b3261e !important; }
.paper .stat .num.cleared { color: #1e7a3c !important; }
.paper .sev-line, .paper .factions span { color: #333 !important; }
.paper .reason { color: #6a4a00 !important; }
.paper .notes, .paper .stat, .paper .report-card, .paper .verdict { background: #fff !important; border-color: #ccc !important; }
.paper .notes, .paper .verdict { color: #333 !important; }
.paper .report-card { padding: 0.6rem; }
.paper .card-text { color: #222 !important; }
.paper .card-text :deep(li) { background: none !important; border-left-color: #bbb !important; }
.paper .card-text :deep(.part.trigger) { color: #0b5d86 !important; }
.paper .card-text :deep(.part.condition) { color: #7a2c96 !important; }
.paper .card-text :deep(.ability-tag) { color: #7a2c96 !important; }
.paper .decision .lines { border-bottom-color: #999; }
.paper .replacements td { color: #222 !important; border-bottom-color: #ddd !important; }

.export-overlay {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  background: var(--bg);
  color: var(--text);
}

@media print {
  .report-page { padding: 0; }
  .no-print { display: none !important; }
}
</style>

<style>
@media print {
  body { background: #fff !important; }
  .btn-back { display: none !important; }
}
</style>
