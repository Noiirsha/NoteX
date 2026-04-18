<script setup lang="ts">
import { useThemeStore } from './stores/theme'

useThemeStore()
</script>

<template>
  <div class="min-h-screen bg-white dark:bg-gray-900 transition-colors duration-500">
    <router-view v-slot="{ Component }">
      <transition name="fade" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>
  </div>
</template>
