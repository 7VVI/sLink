<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { api } from '../api/index.js';
import { toast } from '../stores/toast.js';
import Icon from '../components/Icon.vue';
import Modal from '../components/Modal.vue';
import ConfirmModal from '../components/ConfirmModal.vue';

const router = useRouter();
const groups = ref([]);
const showCreate = ref(false);
const newName = ref('');
const renaming = ref(null);
const renameValue = ref('');
const deleting = ref(null);

const load = async () => {
  try {
    groups.value = await api.listGroups();
  } catch {
    /* 拦截器已提示 */
  }
};

const create = async () => {
  if (!newName.value.trim()) {
    toast('请输入分组名称', 'err');
    return;
  }
  try {
    await api.createGroup(newName.value.trim());
    toast('分组已创建');
    showCreate.value = false;
    newName.value = '';
    await load();
  } catch {
    /* 拦截器已提示 */
  }
};

const rename = async () => {
  if (!renameValue.value.trim()) {
    toast('请输入分组名称', 'err');
    return;
  }
  try {
    await api.renameGroup(renaming.value.id, renameValue.value.trim());
    toast('分组已重命名');
    renaming.value = null;
    await load();
  } catch {
    /* 拦截器已提示 */
  }
};

const remove = async () => {
  const id = deleting.value.id;
  deleting.value = null;
  try {
    await api.deleteGroup(id);
    toast('分组已删除，组内短链已移回未分组');
    await load();
  } catch {
    /* 拦截器已提示 */
  }
};

const openGroup = (id) => router.push({ name: 'links', query: { groupId: String(id) } });

onMounted(load);
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <div class="page-title">分组</div>
        <div class="page-sub">用分组组织短链接，删除分组不影响链接本身</div>
      </div>
      <div class="head-actions">
        <button class="btn btn-primary" @click="showCreate = true">
          <Icon name="plus" :size="15" :sw="2" />新建分组
        </button>
      </div>
    </div>

    <div class="grid3">
      <div v-for="g in groups" :key="g.id" class="card" style="padding:18px">
        <div style="display:flex;justify-content:space-between;align-items:flex-start">
          <div style="display:flex;align-items:center;gap:11px">
            <div class="fav" style="width:36px;height:36px;border-radius:9px"><Icon name="folder" :size="16" /></div>
            <div>
              <div style="font-size:14px;font-weight:600">{{ g.name }}</div>
              <div style="font-size:12px;color:var(--ink-3);margin-top:1px">{{ g.linkCount }} 条短链</div>
            </div>
          </div>
          <div style="display:flex;gap:2px">
            <button class="icon-btn" title="重命名" @click="renaming = g; renameValue = g.name">
              <Icon name="edit" :size="14" />
            </button>
            <button class="icon-btn danger" title="删除分组" @click="deleting = g">
              <Icon name="trash" :size="14" />
            </button>
          </div>
        </div>
        <div style="display:flex;gap:28px;margin:18px 0 12px">
          <div>
            <div class="stat-label">短链数</div>
            <div class="tnum" style="font-size:20px;font-weight:800;letter-spacing:-.02em;margin-top:3px">
              {{ g.linkCount.toLocaleString() }}
            </div>
          </div>
          <div>
            <div class="stat-label">创建于</div>
            <div class="tnum" style="font-size:14px;font-weight:700;margin-top:7px">
              {{ String(g.createTime || '').slice(0, 10) || '—' }}
            </div>
          </div>
        </div>
        <button class="btn btn-ghost btn-sm btn-block" @click="openGroup(g.id)">查看链接</button>
      </div>

      <button
        class="card"
        style="padding:18px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10px;border-style:dashed;background:transparent;cursor:pointer;color:var(--ink-3);min-height:190px"
        @click="showCreate = true"
        @mouseenter="($event) => { $event.currentTarget.style.color = '#111827'; $event.currentTarget.style.borderColor = '#9CA3AF'; }"
        @mouseleave="($event) => { $event.currentTarget.style.color = '#9CA3AF'; $event.currentTarget.style.borderColor = '#E5E7EB'; }"
      >
        <Icon name="plus" :size="20" />
        <span style="font-size:13px;font-weight:500">新建分组</span>
      </button>
    </div>

    <Modal v-if="showCreate" title="新建分组" :width="420" @close="showCreate = false">
      <div class="field">
        <label>分组名称</label>
        <input
          v-model="newName"
          class="input"
          autofocus
          placeholder="例如：Q3 营销活动"
          @keydown.enter="create"
        />
      </div>
      <div style="font-size:12px;color:var(--ink-3)">分组用于组织短链接，可随时重命名或删除，不影响链接统计。</div>
      <template #footer>
        <span style="flex:1" />
        <button class="btn btn-ghost" @click="showCreate = false">取消</button>
        <button class="btn btn-primary" @click="create">创建分组</button>
      </template>
    </Modal>

    <Modal v-if="renaming" title="重命名分组" :width="420" @close="renaming = null">
      <div class="field">
        <label>分组名称</label>
        <input v-model="renameValue" class="input" autofocus @keydown.enter="rename" />
      </div>
      <template #footer>
        <span style="flex:1" />
        <button class="btn btn-ghost" @click="renaming = null">取消</button>
        <button class="btn btn-primary" @click="rename">保存</button>
      </template>
    </Modal>

    <ConfirmModal
      v-if="deleting"
      title="删除分组"
      :desc="`确定删除分组「${deleting.name}」吗？组内 ${deleting.linkCount} 条短链将移回未分组，统计不受影响。`"
      confirm-text="删除分组"
      @close="deleting = null"
      @confirm="remove"
    />
  </div>
</template>
