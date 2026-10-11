<template><ElEmpty :description="`${route.meta.title || '此功能'}将在后续版本启用`" /></template>
<script setup lang="ts">
import { useRoute } from "vue-router";
const route = useRoute();
</script>
