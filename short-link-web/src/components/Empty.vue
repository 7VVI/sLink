<script setup>
import Icon from './Icon.vue';

defineProps({
  icon: { type: String, default: 'link' },
  title: { type: String, default: '暂无数据' },
  sub: { type: String, default: '' },
});
</script>

<template>
  <div class="empty">
    <div class="icon-wrap"><Icon :name="icon" :size="22" /></div>
    <div class="empty-title">{{ title }}</div>
    <div v-if="sub" class="empty-sub">{{ sub }}</div>
  </div>
</template>
