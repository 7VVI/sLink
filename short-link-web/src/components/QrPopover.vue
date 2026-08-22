<script setup>
// 二维码小弹窗：锚定在触发按钮旁侧显示（Teleport + fixed 定位），二维码下方即下载按钮
import { onBeforeUnmount, onMounted, ref, nextTick } from 'vue';
import QRCode from 'qrcode';
import Icon from './Icon.vue';

const props = defineProps({
  shortUrl: { type: String, required: true },
  anchorEl: { type: HTMLElement, default: null },
});
const emit = defineEmits(['close']);

const dataUrl = ref('');
const popRef = ref(null);
const style = ref({ visibility: 'hidden' });

const place = async () => {
  await nextTick();
  const pop = popRef.value;
  if (!pop || !props.anchorEl) {
    return;
  }
  const rect = props.anchorEl.getBoundingClientRect();
  const w = pop.offsetWidth;
  const h = pop.offsetHeight;
  // 优先展示在按钮左上方（操作列靠表格右侧），空间不足时落在下方
  let top = rect.top - h - 8;
  if (top < 76) {
    top = rect.bottom + 8;
  }
  const left = Math.min(Math.max(rect.left + rect.width / 2 - w / 2, 10), window.innerWidth - w - 10);
  style.value = { top: `${top}px`, left: `${left}px` };
};

const onDocDown = (e) => {
  if (popRef.value && !popRef.value.contains(e.target)) {
    emit('close');
  }
};
const onEsc = (e) => {
  if (e.key === 'Escape') {
    emit('close');
  }
};

const download = () => {
  if (!dataUrl.value) {
    return;
  }
  const a = document.createElement('a');
  a.href = dataUrl.value;
  a.download = `qrcode-${props.shortUrl.split('/').pop() || 'slink'}.png`;
  a.click();
};

onMounted(async () => {
  await place();
  document.addEventListener('mousedown', onDocDown, true);
  document.addEventListener('keydown', onEsc);
  dataUrl.value = await QRCode.toDataURL(props.shortUrl, {
    width: 320,
    margin: 1,
    color: { dark: '#111827', light: '#FFFFFF' },
  });
});
onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocDown, true);
  document.removeEventListener('keydown', onEsc);
});
</script>

<template>
  <teleport to="body">
    <div ref="popRef" class="qr-pop" :style="style">
      <div class="qr-pop-head">
        <span style="font-size:11.5px;font-weight:600;color:var(--ink-2)">扫码直达</span>
        <button class="icon-btn" style="width:20px;height:20px" @click="emit('close')">
          <Icon name="x" :size="12" />
        </button>
      </div>
      <div style="padding:9px;border:1px solid var(--hair);border-radius:10px;background:#fff;align-self:center">
        <img v-if="dataUrl" :src="dataUrl" alt="QR" style="width:148px;height:148px;display:block" />
        <div v-else style="width:148px;height:148px" />
      </div>
      <button class="btn btn-primary" style="height:30px;font-size:12.5px" @click="download">
        <Icon name="copy" :size="13" />下载二维码
      </button>
    </div>
  </teleport>
</template>

<style scoped>
.qr-pop {
  position: fixed;
  z-index: 1200;
  width: 186px;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 9px;
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 12px;
  box-shadow: 0 8px 28px rgba(17, 24, 39, 0.14);
}
.qr-pop-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
