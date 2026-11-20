import { computed, ref, toValue, watch, type MaybeRefOrGetter, type Ref } from 'vue'

/** Client-side pagination of a reactive list; back to page 1 when the list or page size changes. */
export function usePagination<T>(items: Ref<T[]>, pageSize: MaybeRefOrGetter<number>) {
  const page = ref(1)
  const pages = computed(() => Math.max(1, Math.ceil(items.value.length / toValue(pageSize))))
  const pageItems = computed(() => {
    const size = toValue(pageSize)
    const current = Math.min(page.value, pages.value)
    return items.value.slice((current - 1) * size, current * size)
  })
  watch([items, () => toValue(pageSize)], () => (page.value = 1))
  return { page, pages, pageItems }
}
