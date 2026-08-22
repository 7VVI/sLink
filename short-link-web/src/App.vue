<script setup>
import { useToast } from './stores/toast.js';

const toastState = useToast();
</script>

<template>
  <router-view />
  <div v-if="toastState.message" class="toast" :class="toastState.type">{{ toastState.message }}</div>
</template>
