<script setup>
import { onMounted, onUnmounted } from 'vue';
import Icon from './Icon.vue';

const props = defineProps({
  title: { type: String, required: true },
  width: { type: Number, default: 560 },
});
const emit = defineEmits(['close']);

const onKey = (e) => {
  if (e.key === 'Escape') {
    emit('close');
  }
};
onMounted(() => document.addEventListener('keydown', onKey));
onUnmounted(() => document.removeEventListener('keydown', onKey));
</script>

<template>
  <div class="overlay" @mousedown.self="emit('close')">
    <div class="modal" :style="{ maxWidth: width + 'px' }">
      <div class="modal-head">
        <div class="modal-title">{{ title }}</div>
        <button class="icon-btn" @click="emit('close')"><Icon name="x" :size="16" /></button>
      </div>
      <div class="modal-body">
        <slot />
      </div>
      <div v-if="$slots.footer" class="modal-foot">
        <slot name="footer" />
      </div>
    </div>
  </div>
</template>
