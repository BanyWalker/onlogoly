<template>
	<div class="relation-list"><div class="section-bar"><strong>关联关系</strong><ElTag size="small" effect="plain">{{ rows.length }}</ElTag></div><ElEmpty v-if="rows.length === 0" description="暂无关系" /><div v-for="row in rows" :key="row.id" class="relation-row" @click="$emit('open', row.id)"><span class="relation-name">{{ row.name }}</span><span>{{ row.sourceType }} → {{ Array.isArray(row.targetType) ? row.targetType.join(', ') : row.targetType }}</span><ElTag size="small" effect="plain">{{ row.compositionKind }}</ElTag></div></div>
</template>
<script setup lang="ts">
import type { GraphRelation } from "../../types";
const props = defineProps<{ objectId: string; relations: GraphRelation[] }>();
defineEmits<{ (e: "open", id: string): void }>();
const rows = props.relations.filter(row => row.sourceType === props.objectId || (Array.isArray(row.targetType) ? row.targetType.includes(props.objectId) : row.targetType === props.objectId));
</script>
<style scoped>
.section-bar, .relation-row { display: flex; align-items: center; }.section-bar { justify-content: space-between; }.relation-row { gap: 8px; padding: 11px 6px; cursor: pointer; border-bottom: 1px solid var(--ctff-border-color-grid); font-size: 12px; }.relation-row:hover { background: var(--ctff-brand-color-background); }.relation-name { min-width: 48px; font-weight: 600; }.relation-row span:nth-child(2) { flex: 1; overflow: hidden; color: var(--ctff-text-color-secondary); text-overflow: ellipsis; white-space: nowrap; }
</style>
