import axios from 'axios';
import { useAuthStore } from '../stores/auth.js';
import { toast } from '../stores/toast.js';

const http = axios.create({ baseURL: '/api', timeout: 15000 });

http.interceptors.request.use((config) => {
  const auth = useAuthStore();
  if (auth.token) {
    config.headers['shortLinkToken'] = auth.token;
  }
  return config;
});

http.interceptors.response.use(
  (response) => {
    const body = response.data;
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code !== 0) {
        toast(body.message || '请求失败', 'err');
        return Promise.reject(new Error(body.message));
      }
      return body.data;
    }
    return body;
  },
  (error) => {
    const body = error.response?.data;
    const code = body?.code;
    const message = body?.message || error.message || '网络异常';
    if (code === 40100 || error.response?.status === 401) {
      const auth = useAuthStore();
      auth.clear();
      if (location.hash !== '#/login') {
        location.hash = '#/login';
      }
      toast('登录已过期，请重新登录', 'err');
    } else {
      toast(message, 'err');
    }
    return Promise.reject(error);
  },
);

/** 统一 API 封装：响应拦截器已解包 Result，直接返回 data */
export const api = {
  // 认证
  login: (data) => http.post('/auth/login', data),
  register: (data) => http.post('/auth/register', data),
  logout: () => http.post('/auth/logout'),
  me: () => http.get('/auth/me'),

  // 短链
  createLink: (data) => http.post('/short-links', data),
  pageLinks: (params) => http.get('/short-links', { params }),
  linkDetail: (code) => http.get(`/short-links/${code}`),
  updateLink: (code, data) => http.put(`/short-links/${code}`, data),
  moveGroup: (code, groupId) => http.put(`/short-links/${code}/group`, { groupId }),
  changeStatus: (code, enabled) => http.put(`/short-links/${code}/status`, { enabled }),
  removeLink: (code) => http.delete(`/short-links/${code}`),

  // 分组
  createGroup: (name) => http.post('/groups', { name }),
  listGroups: () => http.get('/groups'),
  renameGroup: (id, name) => http.put(`/groups/${id}`, { name }),
  deleteGroup: (id) => http.delete(`/groups/${id}`),

  // 域名
  listDomains: () => http.get('/domains'),
  adminListDomains: () => http.get('/admin/domains'),
  adminAddDomain: (data) => http.post('/admin/domains', data),
  adminDomainStatus: (id, enabled) => http.put(`/admin/domains/${id}/status`, { enabled }),
  adminDomainDefault: (id) => http.put(`/admin/domains/${id}/default`),
  adminDeleteDomain: (id) => http.delete(`/admin/domains/${id}`),

  // 回收站
  pageRecycle: (params) => http.get('/recycle-bin', { params }),
  restore: (code) => http.put(`/recycle-bin/${code}/restore`),
  purgeOne: (code) => http.delete(`/recycle-bin/${code}`),

  // 统计
  realtimeStats: (code) => http.get(`/stats/${code}`),
  historyStats: (code, days) => http.get(`/stats/${code}/history`, { params: { days } }),

  // 管理端
  adminPurgeRecycle: () => http.post('/admin/recycle-bin/purge'),
};
