<template>
	<div class="actions-editor">
		<div class="actions-toolbar">
			<div>
				<h3 class="actions-title">动作</h3>
				<p class="actions-hint">动作与当前对象绑定，可从服务目录中选择多个服务。</p>
			</div>
			<ElButton type="primary" :icon="Plus" @click="openSelector">添加动作</ElButton>
		</div>

		<div v-if="modelValue.length" class="action-list">
			<div v-for="action in modelValue" :key="action.id" class="action-row">
				<div class="action-main">
					<div class="action-name-line">
						<strong class="action-name">{{ action.name }}</strong>
						<ElTag size="small" effect="plain">{{ typeLabels[action.serviceType] }}</ElTag>
					</div>
					<div class="action-service">{{ action.serviceName }}</div>
					<div v-if="action.description" class="action-description">{{ action.description }}</div>
				</div>
				<ElButton text type="danger" :icon="Delete" aria-label="移除动作" @click="removeAction(action.id)" />
			</div>
		</div>
		<ElEmpty v-else :image-size="72" description="暂无已配置动作" />

		<ElDialog v-model="selectorVisible" title="选择动作服务" width="760px" append-to-body destroy-on-close>
			<ElTabs v-model="activeType" class="service-type-tabs">
				<ElTabPane v-for="type in serviceTypes" :key="type" :label="typeLabels[type]" :name="type" />
			</ElTabs>
			<div class="selector-toolbar">
				<span class="selector-caption">选择服务（可多选）</span>
				<ElInput v-model="searchText" clearable size="small" class="service-search" placeholder="搜索服务名称">
					<template #prefix><ElIcon><Search /></ElIcon></template>
				</ElInput>
			</div>
			<div v-loading="loading" class="service-options">
				<ElCheckboxGroup v-model="selectedKeys">
					<div v-for="service in filteredServices" :key="serviceKey(service)" class="service-option">
						<ElCheckbox :value="serviceKey(service)">
							<span class="service-option-content">
								<span class="service-option-name">{{ service.name }}</span>
								<span v-if="service.description" class="service-option-description">{{ service.description }}</span>
								<span v-if="service.detail" class="service-option-detail">{{ service.detail }}</span>
							</span>
						</ElCheckbox>
					</div>
				</ElCheckboxGroup>
				<ElEmpty v-if="!loading && !filteredServices.length" :image-size="64" :description="emptyDescription" />
			</div>
			<template #footer>
				<div class="selector-footer">
					<span class="selected-count">已选 {{ selectedKeys.length }} 项</span>
					<div>
						<ElButton @click="selectorVisible = false">取消</ElButton>
						<ElButton type="primary" @click="confirmSelection">确定</ElButton>
					</div>
				</div>
			</template>
		</ElDialog>
	</div>
</template>

<script setup lang="ts">
import { Delete, Plus, Search } from "@element-plus/icons-vue";
import { computed, ref } from "vue";

import type { ActionServiceOption, ActionServiceType, ObjectAction } from "../../types";

type ActionServiceCatalog = Record<ActionServiceType, ActionServiceOption[]>;

const props = withDefaults(defineProps<{
	modelValue: ObjectAction[];
	services: ActionServiceCatalog;
	loading?: boolean;
}>(), { loading: false });

const emit = defineEmits<{ (e: "update:modelValue", value: ObjectAction[]): void }>();

const serviceTypes: ActionServiceType[] = ["API", "MCP", "FUNCTION"];
const typeLabels: Record<ActionServiceType, string> = { API: "API", MCP: "MCP", FUNCTION: "函数" };
const selectorVisible = ref(false);
const activeType = ref<ActionServiceType>("API");
const selectedKeys = ref<string[]>([]);
const searchText = ref("");

const activeServices = computed(() => props.services[activeType.value] ?? []);
const filteredServices = computed(() => {
	const keyword = searchText.value.trim().toLowerCase();
	if (!keyword) return activeServices.value;
	return activeServices.value.filter(service => `${service.name} ${service.description ?? ""} ${service.detail ?? ""}`.toLowerCase().includes(keyword));
});
const emptyDescription = computed(() => activeType.value === "FUNCTION" ? "函数服务暂未接入服务目录" : "暂无可选服务");

function serviceKey(service: ActionServiceOption) {
	return `${service.type}:${service.id}`;
}

function actionKey(action: ObjectAction) {
	return action.id.includes(":") ? action.id : `${action.serviceType}:${action.serviceId}`;
}

function openSelector() {
	selectedKeys.value = props.modelValue.map(actionKey);
	activeType.value = "API";
	searchText.value = "";
	selectorVisible.value = true;
}

function removeAction(id: string) {
	emit("update:modelValue", props.modelValue.filter(action => action.id !== id));
}

function confirmSelection() {
	const optionMap = new Map<string, ActionServiceOption>();
	for (const type of serviceTypes) {
		for (const service of props.services[type] ?? []) optionMap.set(serviceKey(service), service);
	}
	const currentMap = new Map(props.modelValue.map(action => [actionKey(action), action]));
	const actions = selectedKeys.value.map(key => {
		const service = optionMap.get(key);
		if (!service) return currentMap.get(key);
		return {
			id: key,
			name: service.name,
			serviceType: service.type,
			serviceId: service.id,
			serviceName: service.name,
			description: service.description,
			detail: service.detail
		};
	}).filter((action): action is ObjectAction => Boolean(action));
	emit("update:modelValue", actions);
	selectorVisible.value = false;
}
</script>

<style scoped>
.actions-editor {
	display: flex;
	flex-direction: column;
	gap: 14px;
}

.actions-toolbar,
.selector-toolbar,
.selector-footer,
.action-name-line {
	display: flex;
	align-items: center;
}

.actions-toolbar,
.selector-footer {
	justify-content: space-between;
}

.actions-title {
	margin: 0;
	font-size: 15px;
	color: var(--ctff-text-color-primary);
}

.actions-hint,
.action-description,
.service-option-description,
.service-option-detail {
	margin: 4px 0 0;
	font-size: 12px;
	color: var(--ctff-text-color-secondary);
}

.action-list {
	display: flex;
	flex-direction: column;
	border-top: 1px solid var(--ctff-border-color-grid);
}

.action-row {
	display: flex;
	gap: 10px;
	align-items: flex-start;
	padding: 12px 4px;
	border-bottom: 1px solid var(--ctff-border-color-grid);
}

.action-main {
	flex: 1;
	min-width: 0;
}

.action-name-line {
	gap: 8px;
}

.action-name,
.service-option-name {
	overflow: hidden;
	text-overflow: ellipsis;
	color: var(--ctff-text-color-primary);
	white-space: nowrap;
}

.action-service {
	margin-top: 5px;
	font-size: 12px;
	color: var(--ctff-text-color-secondary);
}

.service-type-tabs {
	margin-top: -8px;
}

.selector-toolbar {
	gap: 12px;
	justify-content: space-between;
	margin-bottom: 12px;
}

.selector-caption,
.selected-count {
	font-size: 12px;
	color: var(--ctff-text-color-secondary);
}

.service-search {
	width: 240px;
}

.service-options {
	min-height: 230px;
	max-height: 360px;
	padding: 2px;
	overflow: auto;
}

.service-options :deep(.el-checkbox-group) {
	display: flex;
	flex-direction: column;
	gap: 8px;
}

.service-option {
	padding: 10px 12px;
	cursor: pointer;
	border: 1px solid var(--ctff-border-color-grid);
	border-radius: 6px;
}

.service-option:hover {
	background: var(--ctff-brand-color-background);
	border-color: var(--ctff-brand-color-disabled-border);
}

.service-option :deep(.el-checkbox) {
	align-items: flex-start;
	width: 100%;
	height: auto;
	margin-right: 0;
}

.service-option :deep(.el-checkbox__label) {
	flex: 1;
	min-width: 0;
	padding-left: 9px;
}

.service-option-content {
	display: flex;
	flex-direction: column;
	min-width: 0;
}

.service-option-detail {
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}
</style>
