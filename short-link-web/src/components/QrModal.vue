<script setup>
// 短链二维码弹窗（qrcode 库生成真实二维码，扫码即跳转）
import { onMounted, ref } from 'vue';
import QRCode from 'qrcode';
import Modal from './Modal.vue';
import Icon from './Icon.vue';
import { copyText } from '../stores/toast.js';

const props = defineProps({
  shortUrl: { type: String, required: true },
  longUrl: { type: String, default: '' },
});

const dataUrl = ref('');
const emit = defineEmits(['close']);

onMounted(async () => {
  dataUrl.value = await QRCode.toDataURL(props.shortUrl, {
    width: 260,
    margin: 1,
    color: { dark: '#111827', light: '#FFFFFF' },
  });
});
</script>

<template>
  <Modal title="短链二维码" :width="380" @close="emit('close')">
    <div style="display:flex;flex-direction:column;align-items:center;gap:14px;padding:6px 0 2px">
      <div style="padding:12px;border:1px solid var(--border);border-radius:12px;background:#fff">
        <img v-if="dataUrl" :src="dataUrl" alt="QR" style="width:230px;height:230px;display:block" />
      </div>
      <div class="mono" style="font-size:14px;font-weight:700;word-break:break-all;text-align:center">{{ shortUrl }}</div>
      <div v-if="longUrl" style="font-size:12px;color:var(--ink-3);max-width:300px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">
        → {{ longUrl.replace(/^https?:\/\//, '') }}
      </div>
    </div>
    <template #footer>
      <span style="flex:1;font-size:12;color:var(--ink-3)">扫码直达目标页面</span>
      <button class="btn btn-primary" @click="copyText(shortUrl)"><Icon name="copy" :size="13" />复制短链</button>
    </template>
  </Modal>
</template>
