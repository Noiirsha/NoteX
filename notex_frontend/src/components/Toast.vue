<script setup lang="ts">
import { ref, onMounted } from 'vue'

interface Props {
  message: string
  type?: 'success' | 'error' | 'info' | 'warning'
  duration?: number
}

const props = withDefaults(defineProps<Props>(), {
  type: 'info',
  duration: 3000
})

const emit = defineEmits<{
  close: []
}>()

const visible = ref(false)

onMounted(() => {
  visible.value = true
  setTimeout(() => {
    visible.value = false
    setTimeout(() => emit('close'), 300)
  }, props.duration)
})

const typeClasses = {
  success: 'border-green-300 text-green-600 dark:text-green-400',
  error: 'border-red-300 text-red-600 dark:text-red-400',
  info: 'border-blue-300 text-blue-600 dark:text-blue-400',
  warning: 'border-yellow-300 text-yellow-700 dark:border-yellow-700 dark:text-yellow-300'
}
</script>

<template>
  <Transition
    enter-active-class="transition-all duration-300 ease-out"
    enter-from-class="opacity-0 translate-y-2"
    enter-to-class="opacity-100 translate-y-0"
    leave-active-class="transition-all duration-300 ease-in"
    leave-from-class="opacity-100 translate-y-0"
    leave-to-class="opacity-0 translate-y-2"
  >
    <div
      v-if="visible"
      :class="[
        'fixed top-6 left-1/2 -translate-x-1/2 px-6 py-3 rounded-lg border-1 bg-white dark:bg-gray-900 z-[9999]',
        typeClasses[type]
      ]"
    >
      {{ message }}
    </div>
  </Transition>
</template>
