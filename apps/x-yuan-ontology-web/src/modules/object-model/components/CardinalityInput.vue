<template><div class="cardinality-input"><ElInputNumber :model-value="model.min" :min="0" :precision="0" controls-position="right" @update:model-value="updateMin" /><span>..</span><ElInput :model-value="String(model.max)" placeholder="整数 / N" @update:model-value="updateMax" /></div></template>
<script setup lang="ts">
import type { Cardinality } from "../types";
const model = defineModel<Cardinality>({ required: true });
function updateMax(value: string) { model.value.max = value.toUpperCase() === "N" ? "N" : /^\d+$/.test(value) ? Number(value) : -1; }
function updateMin(value: number | undefined) { model.value.min = value ?? 0; }
</script>
<style scoped>.cardinality-input { display: flex; gap: 6px; align-items: center; width: 100%; }.cardinality-input .el-input-number { width: 90px; flex: 0 0 90px; }</style>
