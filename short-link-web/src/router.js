import { createRouter, createWebHashHistory } from 'vue-router';
import { useAuthStore } from './stores/auth.js';

// hash 路由：与后端 /{code} 跳转路由天然隔离，无需服务端回退配置
export const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('./views/LoginView.vue') },
    {
      path: '/',
      component: () => import('./views/ConsoleView.vue'),
      children: [
        { path: '', redirect: { name: 'links' } },
        { path: 'links', name: 'links', component: () => import('./views/LinksView.vue') },
        { path: 'groups', name: 'groups', component: () => import('./views/GroupsView.vue') },
        { path: 'domains', name: 'domains', component: () => import('./views/DomainsView.vue') },
        { path: 'analytics', name: 'analytics', component: () => import('./views/AnalyticsView.vue') },
        { path: 'trash', name: 'trash', component: () => import('./views/TrashView.vue') },
        { path: 'settings', name: 'settings', component: () => import('./views/SettingsView.vue') },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: { name: 'links' } },
  ],
});

router.beforeEach((to) => {
  const auth = useAuthStore();
  if (to.name !== 'login' && !auth.token) {
    return { name: 'login' };
  }
});
