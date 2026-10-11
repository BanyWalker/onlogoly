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
				<ElButton text type="primary" @click="inlineCreate?.open()">自定义新领域…</ElButton>
			</div>
		</ElFormItem>
		<ElDescriptions v-if="mode === 'edit'" :column="2" size="small" border class="meta-block">
			<ElDescriptionsItem label="修订号">{{ revision }}</ElDescriptionsItem>
			<ElDescriptionsItem label="完整性">{{ completeness === "complete" ? "完整" : "草稿" }}</ElDescriptionsItem>
		</ElDescriptions>
		<DomainInlineCreate ref="inlineCreate" @created="handleDomainCreated" />
	</ElForm>
</template>

<script setup lang="ts">
import { ref } from "vue";

import type { DomainDef } from "../../types";
import DomainInlineCreate from "../DomainInlineCreate.vue";

export interface OverviewDraft {
	id: string;
	name: string;
	definition: string;
	domainId: string;
}

defineProps<{
	mode: "create" | "edit";
	domains: DomainDef[];
	revision?: number;
	completeness?: "draft" | "complete";
}>();

const draft = defineModel<OverviewDraft>({ required: true });
const emit = defineEmits<{ (e: "domain-created", domain: DomainDef): void }>();

const inlineCreate = ref<InstanceType<typeof DomainInlineCreate>>();

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
	align-items: center;
	gap: 8px;
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
</style>
