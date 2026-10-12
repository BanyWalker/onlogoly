<template>
	<div class="relation-list">
		<section v-for="group in groups" :key="group.direction" class="relation-group" :aria-label="group.label">
			<div class="section-bar">
				<strong :title="group.description">{{ group.label }}</strong>
				<ElTag size="small" effect="plain">{{ group.rows.length }}</ElTag>
			</div>
			<div v-if="!group.rows.length" class="relation-empty">暂无{{ group.label }}</div>
			<button v-for="row in group.rows" :key="row.id" type="button" class="relation-row" @click="$emit('open', row.id)">
				<span class="relation-name" :title="row.name">{{ row.name }}</span>
				<span class="relation-endpoints" :title="`${row.sourceType} → ${relationTargetIds(row).join(', ')}`">{{ row.sourceType }} → {{ relationTargetIds(row).join(', ') }}</span>
				<ElTag size="small" effect="plain">{{ compositionLabels[row.compositionKind] }}</ElTag>
			</button>
		</section>
	</div>
</template>

<script setup lang="ts">
import { computed } from "vue";

import { relationTargetIds, type CompositionKind, type GraphRelation } from "../../types";

const props = defineProps<{ objectId: string; relations: GraphRelation[] }>();
defineEmits<{ (e: "open", id: string): void }>();
const compositionLabels: Record<CompositionKind, string> = { Association: "关联", Aggregation: "聚合", Composition: "组合" };
const groups = computed(() => [
	{
		direction: "outgoing",
		label: "出向关系",
		description: "以当前对象为源对象的关系",
		rows: props.relations.filter(row => row.sourceType === props.objectId)
	},
	{
		direction: "incoming",
		label: "入向关系",
		description: "以当前对象为目标对象的关系",
		rows: props.relations.filter(row => relationTargetIds(row).includes(props.objectId))
	}
]);
</script>

<style scoped>
.relation-list {
	display: flex;
	flex-direction: column;
	gap: 24px;
}

.section-bar,
.relation-row {
	display: flex;
	align-items: center;
}

.section-bar {
	justify-content: space-between;
	margin-bottom: 6px;
}

.relation-row {
	gap: 8px;
	width: 100%;
	padding: 11px 6px;
	font: inherit;
	font-size: 12px;
	color: inherit;
	text-align: left;
	cursor: pointer;
	background: transparent;
	border: 0;
	border-bottom: 1px solid var(--ctff-border-color-grid);
}

.relation-row:hover {
	background: var(--ctff-brand-color-background);
}

.relation-row:focus-visible {
	outline: 2px solid var(--ctff-brand-color);
	outline-offset: -2px;
}

.relation-name {
	min-width: 48px;
	font-weight: 600;
}

.relation-endpoints {
	flex: 1;
	min-width: 0;
	overflow: hidden;
	text-overflow: ellipsis;
	color: var(--ctff-text-color-secondary);
	white-space: nowrap;
}

.relation-empty {
	padding: 12px 6px;
	font-size: 12px;
	color: var(--ctff-text-color-secondary);
}
</style>
