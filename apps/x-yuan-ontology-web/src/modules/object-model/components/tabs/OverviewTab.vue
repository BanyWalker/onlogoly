<template>
	<ElForm label-position="top" class="overview-tab">
		<ElFormItem label="名称" required>
			<ElInput v-model="draft.name" placeholder="对象类型名称，如“服务”" />
		</ElFormItem>
		<ElFormItem label="标识（稳定标识，工作空间内唯一）" required>
			<ElInput v-model="draft.id" placeholder="如 OBJ-SERVICE（服务对象）" />
			<div v-if="mode === 'edit'" class="field-hint">修改标识将同步更新全部引用方</div>
		</ElFormItem>
		<ElFormItem label="说明（业务定义）" required>
			<ElInput v-model="draft.definition" type="textarea" :rows="3" placeholder="一句话说明对象是什么、边界含义" />
		</ElFormItem>
		<ElFormItem label="领域" required>
			<div class="domain-row">
				<ElSelect v-model="draft.domainId" placeholder="选择既有领域" class="domain-select">
					<ElOption v-for="domain in domains" :key="domain.domainId" :label="domain.name" :value="domain.domainId">
						<span class="domain-option">
							<i class="domain-dot" :style="{ background: domain.color }" />{{ domain.name }}
						</span>
					</ElOption>
				</ElSelect>
				<ElButton text type="primary" @click="inlineCreate?.open()">添加新的域</ElButton>
			</div>
		</ElFormItem>
		<div v-if="mode === 'edit'" class="meta-block">
			<div class="meta-block-title">对象元信息</div>
			<ElDescriptions :column="2" size="small" border>
				<ElDescriptionsItem label="修订版本">v{{ revision ?? "-" }}</ElDescriptionsItem>
				<ElDescriptionsItem label="完整性状态">
					<ElTag :type="completeness === 'complete' ? 'success' : 'info'" effect="light">
						{{ completeness === "complete" ? "完整" : "草稿" }}
					</ElTag>
				</ElDescriptionsItem>
			</ElDescriptions>
		</div>
		<div v-if="mode === 'edit'" class="model-overview">
			<div class="meta-block-title">模型概览</div>
			<div class="overview-summary-grid">
				<div class="overview-summary-card">
					<span class="overview-summary-label">属性</span>
					<strong>{{ attributes.length }}</strong>
					<span class="overview-summary-hint">已定义属性</span>
				</div>
				<div class="overview-summary-card">
					<span class="overview-summary-label">关系</span>
					<strong>{{ relatedRelations.length }}</strong>
					<span class="overview-summary-hint">关联关系</span>
				</div>
			</div>
			<div class="overview-preview-grid">
				<div class="overview-preview-section">
					<div class="overview-preview-title">属性预览</div>
					<div v-if="attributes.length" class="overview-preview-items">
						<span v-for="attribute in attributes.slice(0, 4)" :key="attribute.id" class="overview-preview-item">{{ attribute.name || attribute.id }}</span>
						<span v-if="attributes.length > 4" class="overview-preview-more">+{{ attributes.length - 4 }}</span>
					</div>
					<span v-else class="overview-preview-empty">暂无属性</span>
				</div>
				<div class="overview-preview-section">
					<div class="overview-preview-title">关系预览</div>
					<div v-if="relatedRelations.length" class="overview-preview-items">
						<span v-for="relation in relatedRelations.slice(0, 4)" :key="relation.id" class="overview-preview-item">{{ relation.name || relation.id }}</span>
						<span v-if="relatedRelations.length > 4" class="overview-preview-more">+{{ relatedRelations.length - 4 }}</span>
					</div>
					<span v-else class="overview-preview-empty">暂无关系</span>
				</div>
			</div>
		</div>
		<DomainInlineCreate ref="inlineCreate" @created="handleDomainCreated" />
	</ElForm>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";

import { relationTargetIds, type AttributeDef, type DomainDef, type GraphRelation } from "../../types";
import DomainInlineCreate from "../DomainInlineCreate.vue";

export interface OverviewDraft {
	id: string;
	name: string;
	definition: string;
	domainId: string;
}

const { mode, domains, revision, completeness, attributes, relations } = defineProps<{
	mode: "create" | "edit";
	domains: DomainDef[];
	revision: number;
	completeness: "draft" | "complete";
	attributes: AttributeDef[];
	relations: GraphRelation[];
}>();

const draft = defineModel<OverviewDraft>({ required: true });
const emit = defineEmits<{ (e: "domain-created", domain: DomainDef): void }>();

const inlineCreate = ref<InstanceType<typeof DomainInlineCreate>>();
const relatedRelations = computed(() => relations.filter(relation => relation.status !== "deprecated" && (relation.sourceType === draft.value.id || relationTargetIds(relation).includes(draft.value.id))));

function handleDomainCreated(domain: DomainDef) {
	emit("domain-created", domain);
	draft.value.domainId = domain.domainId;
}

function validate(): string | null {
	if (!draft.value.id.trim()) return "标识为必填项";
	if (!draft.value.name.trim()) {
		return "名称为必填项";
	}
	if (!draft.value.definition.trim()) {
		return "说明（业务定义）为必填项";
	}
	if (!draft.value.domainId) {
		return "必须选择或创建领域";
	}
	return null;
}

defineExpose({ validate });
</script>

<style scoped>
.domain-row {
	display: flex;
	gap: 8px;
	align-items: center;
	width: 100%;
}

.domain-select {
	flex: 1;
}

.domain-dot {
	display: inline-block;
	width: 8px;
	height: 8px;
	margin-right: 6px;
	border-radius: 50%;
}

.field-hint {
	margin-top: 4px;
	font-size: 12px;
	color: var(--el-text-color-secondary);
}

.meta-block {
	margin-top: 8px;
}

.meta-block-title {
	margin-bottom: 8px;
	font-size: 14px;
	font-weight: 600;
	color: var(--el-text-color-primary);
}

.model-overview {
	padding-top: 18px;
}

.overview-summary-grid {
	display: grid;
	grid-template-columns: repeat(2, minmax(0, 1fr));
	gap: 8px;
}

.overview-summary-card {
	display: grid;
	grid-template-columns: 1fr auto;
	gap: 2px 8px;
	padding: 10px 12px;
	background: var(--ctff-bg-color-container);
	border: 1px solid var(--ctff-border-color-grid);
	border-radius: 8px;
}

.overview-summary-label {
	font-size: 12px;
	color: var(--ctff-text-color-secondary);
}

.overview-summary-card strong {
	grid-row: span 2;
	align-self: center;
	font-size: 20px;
	font-weight: 600;
	color: var(--ctff-brand-color);
}

.overview-summary-hint {
	font-size: 11px;
	color: var(--ctff-text-color-tertiary);
}

.overview-preview-grid {
	display: grid;
	gap: 12px;
	margin-top: 12px;
}

.overview-preview-section {
	padding: 10px 12px;
	background: var(--ctff-bg-color-page);
	border: 1px solid var(--ctff-border-color-grid);
	border-radius: 8px;
}

.overview-preview-title {
	margin-bottom: 7px;
	font-size: 12px;
	font-weight: 600;
	color: var(--ctff-text-color-primary);
}

.overview-preview-items {
	display: flex;
	flex-wrap: wrap;
	gap: 6px;
}

.overview-preview-item,
.overview-preview-more {
	max-width: 100%;
	padding: 3px 8px;
	overflow: hidden;
	text-overflow: ellipsis;
	font-size: 11px;
	color: var(--ctff-brand-color-active);
	white-space: nowrap;
	background: var(--ctff-brand-color-background);
	border-radius: 12px;
}

.overview-preview-more {
	color: var(--ctff-text-color-tertiary);
	background: var(--ctff-bg-color-container);
}

.overview-preview-empty {
	font-size: 12px;
	color: var(--ctff-text-color-tertiary);
}
</style>
