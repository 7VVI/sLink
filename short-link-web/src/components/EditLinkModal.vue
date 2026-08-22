<script setup>
// 编辑短链弹窗：修改目标链接 / 备注 / 分组（短码与域名不变，跳转秒级生效）
import { ref } from 'vue';
import { api } from '../api/index.js';
import { toast } from '../stores/toast.js';
import Modal from './Modal.vue';
import Icon from './Icon.vue';

const props = defineProps({
  link: { type: Object, required: true },
  groups: { type: Array, default: () => [] },
});
const emit = defineEmits(['close', 'updated']);

const longUrl = ref(props.link.longUrl || '');
const title = ref(props.link.title || '');
const groupId = ref(props.link.groupId ? String(props.link.groupId) : '0');
const submitting = ref(false);

const submit = async () => {
  if (submitting.value) {
    return;
  }
  if (!longUrl.value.trim()) {
    toast('请填写目标链接', 'err');
    return;
  }
  submitting.value = true;
  try {
    // ID 全程按字符串传递（后端 Long 序列化为字符串防精度丢失）
    await api.updateLink(props.link.code, {
      longUrl: longUrl.value.trim(),
      title: title.value.trim() || null,
      groupId: groupId.value === '0' ? 0 : groupId.value,
    });
    toast('已保存，跳转目标已更新');
    emit('updated');
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false;
  }
};
</script>

<template>
  <Modal title="编辑短链" :width="520" @close="!submitting && emit('close')">
    <div
      style="background:#FAFAF8;border:1px solid var(--hair);border-radius:8px;padding:10px 12px;display:flex;align-items:center;gap:10px;margin-bottom:16px"
    >
      <Icon name="link" :size="14" style="color:var(--ink-3)" />
      <span class="mono" style="font-size:13px;font-weight:700">{{ link.shortUrl }}</span>
      <span style="font-size:12px;color:var(--ink-3)">短码与域名不可修改</span>
    </div>
    <div class="field">
      <label>目标链接</label>
      <input v-model="longUrl" class="input" placeholder="https://example.com/very/long/path" />
      <div class="hint">保存后缓存立即失效，新跳转目标秒级生效，历史统计保留</div>
    </div>
    <div class="row">
      <div class="field">
        <label>分组</label>
        <select v-model="groupId" class="select">
          <option value="0">未分组</option>
          <option v-for="g in groups" :key="g.id" :value="String(g.id)">{{ g.name }}</option>
        </select>
      </div>
      <div class="field">
        <label>描述（可选）</label>
        <textarea v-model="title" class="input" rows="2" placeholder="用于描述这个网站，方便在列表中识别…" />
      </div>
    </div>
    <template #footer>
      <span style="flex:1" />
      <button class="btn btn-ghost" :disabled="submitting" @click="emit('close')">取消</button>
      <button class="btn btn-primary" :disabled="submitting" @click="submit">
        {{ submitting ? '保存中…' : '保存修改' }}
      </button>
    </template>
  </Modal>
</template>
