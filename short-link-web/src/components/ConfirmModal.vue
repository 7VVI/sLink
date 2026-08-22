<script setup>
import Modal from './Modal.vue';
import Icon from './Icon.vue';

defineProps({
  title: { type: String, default: '确认操作' },
  desc: { type: String, required: true },
  confirmText: { type: String, default: '确认' },
  danger: { type: Boolean, default: true },
});
const emit = defineEmits(['close', 'confirm']);
</script>

<template>
  <Modal :title="title" :width="420" @close="emit('close')">
    <div style="display:flex;gap:12px;padding:4px 0">
      <div
        class="icon-wrap"
        :style="{
          width: '40px', height: '40px', borderRadius: '10px', flexShrink: 0,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          background: danger ? '#FEF2F2' : 'var(--soft)',
          color: danger ? 'var(--red)' : 'var(--ink-2)',
        }"
      >
        <Icon :name="danger ? 'alert' : 'info'" :size="18" />
      </div>
      <div style="font-size:13px;color:var(--ink-2);line-height:1.7">{{ desc }}</div>
    </div>
    <template #footer>
      <span style="flex:1" />
      <button class="btn btn-ghost" @click="emit('close')">取消</button>
      <button
        class="btn btn-primary"
        :style="danger ? { background: 'var(--red)' } : null"
        @click="emit('confirm')"
      >
        {{ confirmText }}
      </button>
    </template>
  </Modal>
</template>
