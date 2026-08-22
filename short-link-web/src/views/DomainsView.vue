<script setup>
// 域名管理：管理员可增删改与设默认；普通用户仅查看可用域名
import { onMounted, ref } from 'vue';
import { api } from '../api/index.js';
import { useAuthStore } from '../stores/auth.js';
import { toast } from '../stores/toast.js';
import Icon from '../components/Icon.vue';
import Empty from '../components/Empty.vue';
import Modal from '../components/Modal.vue';
import ConfirmModal from '../components/ConfirmModal.vue';

const auth = useAuthStore();
const domains = ref([]);
const showAdd = ref(false);
const deleting = ref(null);
const newDomain = ref('');
const newName = ref('');
const newIsDefault = ref(false);

const load = async () => {
  try {
    domains.value = auth.isAdmin ? await api.adminListDomains() : await api.listDomains();
  } catch {
    /* 拦截器已提示 */
  }
};

const add = async () => {
  const clean = newDomain.value.trim().replace(/\/+$/, '');
  if (!clean) {
    toast('请输入域名', 'err');
    return;
  }
  try {
    await api.adminAddDomain({
      domain: clean,
      name: newName.value.trim() || null,
      isDefault: newIsDefault.value,
    });
    toast('域名已添加');
    showAdd.value = false;
    newDomain.value = '';
    newName.value = '';
    newIsDefault.value = false;
    await load();
  } catch {
    /* 拦截器已提示 */
  }
};

const toggle = async (d) => {
  try {
    await api.adminDomainStatus(d.id, Number(d.status) !== 1);
    toast(Number(d.status) === 1 ? '域名已停用' : '域名已启用');
    await load();
  } catch {
    /* 拦截器已提示 */
  }
};

const setDefault = async (d) => {
  try {
    await api.adminDomainDefault(d.id);
    toast(`已将 ${d.domain} 设为默认域名`);
    await load();
  } catch {
    /* 拦截器已提示 */
  }
};

const remove = async () => {
  const id = deleting.value.id;
  deleting.value = null;
  try {
    await api.adminDeleteDomain(id);
    toast('域名已删除');
    await load();
  } catch {
    /* 拦截器已提示 */
  }
};

onMounted(load);
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <div class="page-title">域名管理</div>
        <div class="page-sub">{{ auth.isAdmin ? '维护短链域名池，默认域名用于未指定域名的短链' : '当前可用的短链域名' }}</div>
      </div>
      <div class="head-actions">
        <button v-if="auth.isAdmin" class="btn btn-primary" @click="showAdd = true">
          <Icon name="plus" :size="15" :sw="2" />添加域名
        </button>
      </div>
    </div>

    <div class="card">
      <div style="overflow-x:auto">
        <table class="tbl">
          <thead>
            <tr>
              <th>域名</th>
              <th>备注</th>
              <th>状态</th>
              <th v-if="auth.isAdmin" style="text-align:right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="d in domains" :key="d.id">
              <td>
                <span style="display:inline-flex;align-items:center;gap:9px">
                  <span class="fav"><Icon name="globe" :size="13" /></span>
                  <span class="mono" style="font-weight:600;font-size:13px">{{ d.domain }}</span>
                  <span v-if="d.isDefault" class="pill gray">默认</span>
                </span>
              </td>
              <td style="color:var(--ink-2);font-size:12.5px">{{ d.name || '—' }}</td>
              <td>
                <span class="pill" :class="Number(d.status) === 1 ? 'green' : 'gray'">
                  {{ Number(d.status) === 1 ? '启用' : '停用' }}
                </span>
              </td>
              <td v-if="auth.isAdmin">
                <div style="display:flex;justify-content:flex-end;gap:2px">
                  <button v-if="!d.isDefault" class="btn btn-ghost btn-sm" @click="setDefault(d)">设为默认</button>
                  <button
                    class="icon-btn"
                    :title="Number(d.status) === 1 ? '停用' : '启用'"
                    @click="toggle(d)"
                  >
                    <Icon :name="Number(d.status) === 1 ? 'eye' : 'refresh'" :size="14" />
                  </button>
                  <button v-if="!d.isDefault" class="icon-btn danger" title="删除" @click="deleting = d">
                    <Icon name="trash" :size="14" />
                  </button>
                </div>
              </td>
            </tr>
            <tr v-if="!domains.length">
              <td :colspan="auth.isAdmin ? 4 : 3">
                <Empty icon="globe" title="暂无域名" sub="添加第一个短链域名后即可创建短链" />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div style="padding:14px 18px;border-top:1px solid #F2F2F0;display:flex;align-items:center;gap:8px">
        <Icon name="info" :size="14" style="color:var(--ink-3)" />
        <span style="font-size:12.5px;color:var(--ink-3)">
          跳转解析与域名无关，任意已配置域名下的短码均可正常跳转；默认域名不可删除或停用。
        </span>
      </div>
    </div>

    <Modal v-if="showAdd" title="添加域名" :width="480" @close="showAdd = false">
      <div class="field">
        <label>域名（含协议）</label>
        <input v-model="newDomain" class="input" autofocus placeholder="https://link.mybrand.com" @keydown.enter="add" />
        <div class="hint">需以 http:// 或 https:// 开头，仅域名（可含端口），不带路径</div>
      </div>
      <div class="field">
        <label>备注名（可选）</label>
        <input v-model="newName" class="input" placeholder="例如：官网主域" />
      </div>
      <label style="display:flex;align-items:center;gap:9px;font-size:13px;color:var(--ink-2);cursor:pointer">
        <input v-model="newIsDefault" type="checkbox" style="accent-color:#111827;width:15px;height:15px" />
        设为默认域名（当前默认域名将被替换）
      </label>
      <template #footer>
        <span style="flex:1" />
        <button class="btn btn-ghost" @click="showAdd = false">取消</button>
        <button class="btn btn-primary" @click="add">添加域名</button>
      </template>
    </Modal>

    <ConfirmModal
      v-if="deleting"
      title="删除域名"
      :desc="`确定删除域名 ${deleting.domain} 吗？已有短链记录仍展示该域名前缀，但新短链不能再使用它。`"
      confirm-text="删除域名"
      @close="deleting = null"
      @confirm="remove"
    />
  </div>
</template>
