import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getBusinessTypes } from '../api/business'

export const useCommonStore = defineStore('common', () => {
  const businessTypes = ref([])
  const counters = ref([])
  const currentCounterId = ref(null)

  async function fetchBusinessTypes() {
    const res = await getBusinessTypes()
    businessTypes.value = res.data || res || []
  }

  return {
    businessTypes,
    counters,
    currentCounterId,
    fetchBusinessTypes,
  }
})
