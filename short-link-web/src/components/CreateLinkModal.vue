<script setup>
// 新建短链弹窗：单个创建 / 批量导入（逐条调用创建接口）
import { computed, onMounted, ref } from 'vue';
import { api } from '../api/index.js';
import { toast } from '../stores/toast.js';
import Icon from './Icon.vue';
import Modal from './Modal.vue';

const props = defineProps({
  groups: { type: Array, default: () => [] },
});
const emit = defineEmits(['close', 'created']);

const tab = ref('single');
const domains = ref([]);

const longUrl = ref('');
const title = ref('');
const domainId = ref('');
const groupId = ref('0');
const expire = ref('forever');
const bulkText = ref('');
const submitting = ref(false);
const progress = ref(null);

onMounted(async () => {
  try {
    domains.value = await api.listDomains();
    const def = domains.value.find((d) => d.isDefault) || domains.value[0];
    domainId.value = def ? String(def.id) : '';
  } catch {
    /* 拦截器已提示 */
  }
});

const currentDomain = computed(() =>
  domains.value.find((d) => String(d.id) === domainId.value),
);

const expireDays = computed(() => (expire.value === 'forever' ? null : Number(expire.value)));

const bulkLines = computed(() =>
  bulkText.value.split('\n').map((s) => s.trim()).filter(Boolean),
);

const submit = async () => {
  if (submitting.value) {
    return;
  }
  if (tab.value === 'single') {
    if (!longUrl.value.trim()) {
      toast('请填写目标链接', 'err');
      return;
    }
    submitting.value = true;
    try {
      // ID 全程按字符串传递（后端 Long 序列化为字符串防精度丢失，字符串可直接反序列化回 Long）
      const created = await api.createLink({
        longUrl: longUrl.value.trim(),
        title: title.value.trim() || null,
        domainId: domainId.value ? domainId.value : null,
        groupId: groupId.value === '0' ? 0 : groupId.value,
        expireDays: expireDays.value,
      });
      toast(`创建成功：${created.shortUrl}`);
      emit('created');
    } catch {
      /* 拦截器已提示 */
    } finally {
      submitting.value = false;
    }
  } else {
    if (!bulkLines.value.length) {
      toast('请粘贴要转换的链接', 'err');
      return;
    }
    submitting.value = true;
    let ok = 0;
    let fail = 0;
    for (let i = 0; i < bulkLines.value.length; i++) {
      progress.value = { done: i, total: bulkLines.value.length };
      try {
        await api.createLink({
          longUrl: bulkLines.value[i],
          domainId: domainId.value ? domainId.value : null,
          groupId: groupId.value === '0' ? 0 : groupId.value,
          expireDays: expireDays.value,
        });
        ok++;
      } catch {
        fail++;
      }
    }
    progress.value = null;
    submitting.value = false;
    toast(`批量创建完成：成功 ${ok} 条${fail ? `，失败 ${fail} 条` : ''}`, fail ? 'err' : 'ok');
    if (ok > 0) {
      emit('created');
    }
  }
};
</script>

<template>
  <Modal title="新建短链接" :width="720" @close="!submitting && emit('close')">
    <div class="seg">
      <button :class="{ on: tab === 'single' }" @click="tab = 'single'">单个创建</button>
      <button :class="{ on: tab === 'bulk' }" @click="tab = 'bulk'">批量导入</button>
    </div>
    <div style="height:16px" />

    <template v-if="tab === 'single'">
      <div class="field">
        <label>目标链接</label>
        <div style="display:flex;gap:8px">
          <input v-model="longUrl" class="input" placeholder="https://example.com/very/long/path" />
        </div>
        <div class="hint">仅支持 http/https 链接，创建后短码由发号器自动生成，全局唯一</div>
      </div>
      <div class="row">
        <div class="field">
          <label>短链域名</label>
          <select v-model="domainId" class="select">
            <option v-for="d in domains" :key="d.id" :value="String(d.id)">
              {{ d.domain }}{{ d.isDefault ? ' · 默认' : '' }}
            </option>
          </select>
        </div>
        <div class="field">
          <label>分组</label>
          <select v-model="groupId" class="select">
            <option value="0">未分组</option>
            <option v-for="g in groups" :key="g.id" :value="String(g.id)">{{ g.name }}</option>
          </select>
        </div>
      </div>
      <div class="row">
        <div class="field">
          <label>有效期</label>
          <select v-model="expire" class="select">
            <option value="forever">永久有效</option>
            <option value="365">365 天</option>
            <option value="30">30 天</option>
            <option value="7">7 天</option>
          </select>
        </div>
      </div>
      <div class="field" style="margin-bottom:0">
        <label>描述（可选）</label>
        <textarea v-model="title" class="input" rows="2" placeholder="用于描述这个网站，方便在列表中识别…" />
      </div>
      <div
        style="background:#F8FAFC;border:1px solid var(--hair);border-radius:8px;padding:12px 14px;display:flex;align-items:center;gap:10px"
      >
        <Icon name="info" :size="14" style="color:var(--ink-3)" />
        <span style="font-size:12.5px;color:var(--ink-2)">
          短链预览：<b class="mono">{{ currentDomain ? currentDomain.domain : '' }}/{{ longUrl ? 'xxxxxxxx' : '……' }}</b>
        </span>
      </div>
    </template>

    <template v-else>
      <div class="field">
        <label>粘贴链接列表</label>
        <textarea
          v-model="bulkText"
          class="input"
          rows="7"
          placeholder="https://example.com/page/1&#10;https://example.com/page/2&#10;https://example.com/page/3"
        />
        <div class="hint">每行一条长链接，使用相同域名 / 分组 / 有效期批量生成短链</div>
      </div>
      <div class="row">
        <div class="field">
          <label>短链域名</label>
          <select v-model="domainId" class="select">
            <option v-for="d in domains" :key="d.id" :value="String(d.id)">
              {{ d.domain }}{{ d.isDefault ? ' · 默认' : '' }}
            </option>
          </select>
        </div>
        <div class="field">
          <label>分组</label>
          <select v-model="groupId" class="select">
            <option value="0">未分组</option>
            <option v-for="g in groups" :key="g.id" :value="String(g.id)">{{ g.name }}</option>
          </select>
        </div>
        <div class="field">
          <label>有效期</label>
          <select v-model="expire" class="select">
            <option value="forever">永久有效</option>
            <option value="365">365 天</option>
            <option value="30">30 天</option>
          </select>
        </div>
      </div>
      <div v-if="bulkText.trim()" style="font-size:12.5px;color:var(--ink-2);background:#F8FAFC;border:1px solid var(--hair);border-radius:8px;padding:10px 12px">
        已识别 <b class="tnum">{{ bulkLines.length }}</b> 条链接，将批量生成短链
      </div>
    </template>

    <template #footer>
      <span style="flex:1;font-size:12px;color:var(--ink-3);text-align:left">
        {{ progress ? `正在创建 ${progress.done + 1} / ${progress.total} …` : tab === 'single' ? '创建后可随时上下线，统计不受影响' : '逐条创建，失败条目会单独提示' }}
      </span>
      <button class="btn btn-ghost" :disabled="submitting" @click="emit('close')">取消</button>
      <button class="btn btn-primary" :disabled="submitting" @click="submit">
        {{ submitting ? '创建中…' : tab === 'single' ? '创建短链' : '批量生成' }}
      </button>
    </template>
  </Modal>
</template>
