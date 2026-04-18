<script setup lang="ts">
import { ref, computed } from 'vue'
import type { NoteGroup } from '@/types/api'

// 定义文件夹选项接口
interface FolderOption {
  id: number
  name: string
  level: number
  hasChildren: boolean
}

// 组件 props
interface Props {
  // 所有笔记组列表
  noteGroups: NoteGroup[]
  // 当前选中的文件夹ID
  selectedGroupId: number
  // 是否包含根目录选项
  includeRoot?: boolean
  // 根目录显示名称
  rootName?: string
  // 禁用的文件夹ID（用于排除当前所在文件夹）
  disabledGroupId?: number
}

const props = withDefaults(defineProps<Props>(), {
  includeRoot: false,
  rootName: '根目录',
  disabledGroupId: -1
})

// 事件
const emit = defineEmits<{
  'update:selectedGroupId': [value: number]
}>()

// 展开的文件夹ID集合
const expandedFolders = ref<Set<number>>(new Set())

// 计算文件夹选项（带层级信息）
const folderOptions = computed<FolderOption[]>(() => {
  const options: FolderOption[] = []
  
  // 如果包含根目录，则添加根目录选项
  if (props.includeRoot) {
    options.push({
      id: 0,
      name: props.rootName,
      level: 0,
      hasChildren: props.noteGroups.some(g => g.parentGroupId === 0)
    })
  }
  
  // 递归构建文件夹选项树
  const buildOptions = (groups: NoteGroup[], parentId: number = 0, level: number = 0) => {
    groups
      .filter(group => group.parentGroupId === parentId)
      .forEach(group => {
        // 如果设置了禁用ID且与当前文件夹ID匹配，则跳过
        if (props.disabledGroupId !== -1 && group.id === props.disabledGroupId) {
          return
        }
        
        // 检查是否有子文件夹
        const hasChildren = groups.some(g => g.parentGroupId === group.id)
        
        options.push({
          id: group.id,
          name: group.groupName,
          level,
          hasChildren
        })
        
        // 只有展开时才递归渲染子文件夹
        if (hasChildren && expandedFolders.value.has(group.id)) {
          buildOptions(groups, group.id, level + 1)
        }
      })
  }
  
  buildOptions(props.noteGroups)
  return options
})

// 处理文件夹选择
const handleSelectFolder = (groupId: number) => {
  emit('update:selectedGroupId', groupId)
}

// 切换展开/收起状态
const toggleExpand = (groupId: number) => {
  if (expandedFolders.value.has(groupId)) {
    expandedFolders.value.delete(groupId)
  } else {
    expandedFolders.value.add(groupId)
  }
}

// 检查是否为选中状态
const isSelected = (groupId: number) => {
  return groupId === props.selectedGroupId
}

// 检查是否展开
const isExpanded = (groupId: number) => {
  return expandedFolders.value.has(groupId)
}

// 计算缩进宽度（包含基础左边距）
const getIndent = (level: number) => {
  return `${16 + level * 16}px` // 基础左边距16px，每个层级额外缩进16px
}
</script>

<template>
  <div class="max-h-64 overflow-y-auto border border-gray-200 dark:border-gray-700 rounded-lg">
    <button
      v-for="option in folderOptions"
      :key="option.id"
      @click="handleSelectFolder(option.id)"
      class="w-full px-4 py-3 text-left hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors flex items-center gap-2"
      :class="{ 'bg-gray-100 dark:bg-gray-700': isSelected(option.id) }"
      :style="{ paddingLeft: getIndent(option.level) }"
    >
      <!-- 展开/收起箭头 -->
      <svg
        v-if="option.hasChildren"
        @click.stop="toggleExpand(option.id)"
        class="w-4 h-4 text-gray-500 dark:text-gray-400 flex-shrink-0 transition-transform duration-200"
        :class="{ 'rotate-90': isExpanded(option.id) }"
        fill="none"
        stroke="currentColor"
        viewBox="0 0 24 24"
      >
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
      </svg>
      <!-- 占位符，保持对齐 -->
      <span v-else class="w-4 h-4 flex-shrink-0"></span>
      
      <!-- 文件夹图标 -->
      <svg
        class="w-5 h-5 text-gray-500 dark:text-gray-400 flex-shrink-0"
        fill="none"
        stroke="currentColor"
        viewBox="0 0 24 24"
      >
        <!-- 根目录图标 -->
        <path
          v-if="option.id === 0"
          stroke-linecap="round"
          stroke-linejoin="round"
          stroke-width="2"
          d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z"
        />
        <!-- 文件夹图标 -->
        <path
          v-else
          d="M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z"
        />
      </svg>
      <span class="text-gray-900 dark:text-white">{{ option.name }}</span>
    </button>
  </div>
</template>
