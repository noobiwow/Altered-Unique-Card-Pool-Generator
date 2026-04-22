<template>
  <div>
    <!-- Grid -->
    <div class="visualizer-grid">
      <div v-for="ref in paginatedRefs" :key="ref" class="visualizer-card">
        <AlteredCard :card-ref="ref"/>
      </div>
    </div>

    <!-- Pagination Controls -->
    <div class="pagination">
      <button
        @click="goToPreviousPage"
        :disabled="currentPage === 1"
      >
        Prev
      </button>

      <span class="page-info">
        Page {{ currentPage }} / {{ totalPages }}
      </span>

      <button
        @click="goToNextPage"
        :disabled="currentPage === totalPages"
      >
        Next
      </button>
    </div>
  </div>
</template>

<script>
import { AlteredCard } from 'altered-tcg';

export default {
  name: "VisualizerGrid",

  components: {
    AlteredCard
  },

  props: {
    refs: {
      type: Array,
      required: true
    }
  },

  data() {
    return {
      currentPage: 1,
      itemsPerPage: 20
    };
  },

  computed: {
    paginatedRefs() {
      const start = (this.currentPage - 1) * this.itemsPerPage;
      const end = start + this.itemsPerPage;
      return this.refs.slice(start, end);
    },

    totalPages() {
      return Math.ceil(this.refs.length / this.itemsPerPage) || 1;
    }
  },

  methods: {
    goToNextPage() {
      if (this.currentPage < this.totalPages) {
        this.currentPage++;
      }
    },

    goToPreviousPage() {
      if (this.currentPage > 1) {
        this.currentPage--;
      }
    }
  },

  watch: {
    refs: {
      handler() {
        // Reset pagination if dataset changes
        this.currentPage = 1;
      },
      deep: true
    }
  }
};
</script>

<style scoped>

.visualizer-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  padding: 16px;
  box-sizing: border-box;
}

.visualizer-card {
  width: 100%;
  max-width: 100%;
}

.visualizer-card > * > * {
  border-radius: 8px;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
}

button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-info {
  font-weight: bold;
}
</style>

