<template>
	<aside class="fact-type-panel">
		<div class="fact-type-heading">
			<strong>实例清单</strong><span>（{{ visibleCount }} / {{ totalCount }}）</span>
		</div>
		<nav class="fact-type-scroll" aria-label="对象类型">
			<button
				v-for="type in types"
				:key="type.id"
				type="button"
				class="fact-type-item"
				:class="{ 'is-selected': selectedTypes.includes(type.id) }"
				:aria-pressed="selectedTypes.includes(type.id)"
				@click="choose(type.id)"
			>
				<i class="fact-type-dot" :style="{ background: type.color }" aria-hidden="true" />
				<span class="fact-type-name">{{ type.name }}</span
				><span class="fact-type-count">{{ type.instanceCount }}</span>
			</button>
			<p v-if="!types.length" class="fact-type-empty">暂无对象类型</p>
		</nav>
		<button v-if="selectedTypes.length" type="button" class="fact-type-reset" @click="emit('select', [])">
			清除筛选
		</button>
	</aside>
</template>

<script setup lang="ts">
import type { FactTypeItem } from "../types";

const props = defineProps<{
	types: FactTypeItem[];
	selectedTypes: string[];
	visibleCount: number;
	totalCount: number;
}>();
const emit = defineEmits<{ (event: "select", ids: string[]): void }>();
function choose(id: string) {
	emit(
		"select",
		props.selectedTypes.includes(id) ? props.selectedTypes.filter(value => value !== id) : [...props.selectedTypes, id]
	);
}
</script>

<style scoped>
.fact-type-panel {
	display: flex;
	flex: 0 0 248px;
	flex-direction: column;
	min-height: 0;
	overflow: hidden;
	background: var(--el-bg-color);
	border: 0;
	border-right: 1px solid var(--el-border-color-lighter);
	border-radius: 0;
}

.fact-type-heading {
	display: flex;
	flex-wrap: wrap;
	gap: 5px;
	align-items: baseline;
	padding: 14px 14px 10px;
	font-size: 13px;
	color: var(--el-text-color-primary);
}

.fact-type-heading > span {
	font-size: 12px;
	font-weight: 400;
	color: var(--el-text-color-secondary);
}

.fact-type-scroll {
	flex: 1;
	padding: 0 8px 10px;
	overflow: auto;
}

.fact-type-item {
	display: flex;
	gap: 8px;
	align-items: center;
	width: 100%;
	padding: 10px 8px;
	margin: 3px 0;
	font: inherit;
	font-size: 12px;
	color: var(--el-text-color-secondary);
	text-align: left;
	cursor: pointer;
	background: transparent;
	border: 1px solid transparent;
	border-radius: 8px;
}

.fact-type-item:hover {
	background: var(--el-fill-color-light);
}

.fact-type-item.is-selected {
	color: var(--el-text-color-regular);
	background: var(--el-color-primary-light-9);
	border-color: var(--el-color-primary-light-7);
}

.fact-type-item:focus-visible,
.fact-type-reset:focus-visible {
	outline: 2px solid var(--el-color-primary);
	outline-offset: -2px;
}

.fact-type-dot {
	flex-shrink: 0;
	width: 8px;
	height: 8px;
	border-radius: 50%;
}

.fact-type-name {
	flex: 1;
	min-width: 0;
	overflow-wrap: anywhere;
}

.fact-type-count {
	font-variant-numeric: tabular-nums;
	color: var(--el-text-color-placeholder);
}

.fact-type-reset {
	padding: 10px 14px;
	font: inherit;
	font-size: 12px;
	color: var(--el-color-primary);
	cursor: pointer;
	background: transparent;
	border: 0;
	border-top: 1px solid var(--el-border-color-lighter);
}

.fact-type-empty {
	padding: 12px 8px;
	font-size: 12px;
	color: var(--el-text-color-placeholder);
}

@media (width <= 960px) {
	.fact-type-panel {
		flex-basis: 190px;
	}
}
</style>
