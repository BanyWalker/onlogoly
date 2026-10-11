<template>
	<ElForm label-position="top" class="relation-editor">
		<ElAlert v-if="creating" title="关系端点已由画布连线预填，可继续调整后保存。" type="info" :closable="false" show-icon />
		<ElAlert v-if="validationError" :title="validationError" type="error" :closable="false" show-icon />
		<ElFormItem label="关系名称" required>
			<ElInput v-model="draft.name" placeholder="如：支撑、归属、备份于" />
		</ElFormItem>
		<ElFormItem label="业务定义">
			<ElInput v-model="draft.definition" type="textarea" :rows="2" />
		</ElFormItem>
		<div class="endpoint-grid">
			<ElFormItem label="源对象" required>
				<ElSelect v-model="draft.sourceType">
					<ElOption v-for="item in objects" :key="item.id" :label="item.name" :value="item.id" />
				</ElSelect>
			</ElFormItem>
			<ElFormItem label="目标对象" required>
				<ElSelect v-model="targetId">
					<ElOption v-for="item in objects" :key="item.id" :label="item.name" :value="item.id" />
				</ElSelect>
			</ElFormItem>
		</div>
		<ElFormItem label="基数" required>
			<ElSelect v-model="cardinalityPreset" placeholder="请选择基数" class="cardinality-select">
				<ElOption label="1:1（一对一）" value="1:1" />
				<ElOption label="1:n（一对多）" value="1:n" />
				<ElOption label="n:1（多对一）" value="n:1" />
				<ElOption label="m:n（多对多）" value="m:n" />
			</ElSelect>
			<div class="field-hint">按“源对象 : 目标对象”表示数量关系。</div>
		</ElFormItem>
		<ElFormItem label="关系类型">
			<ElRadioGroup v-model="draft.compositionKind" class="relation-type-options" @change="compositionChanged">
				<ElRadio v-for="kind in relationKinds" :key="kind.value" :value="kind.value" class="relation-type-option">
					<span class="relation-type-label">{{ kind.label }}</span>
					<span class="relation-type-description">{{ kind.description }}</span>
				</ElRadio>
			</ElRadioGroup>
		</ElFormItem>
		<div v-if="draft.compositionKind !== 'Association'" class="whole-part-block">
			<div class="endpoint-grid">
				<ElFormItem label="整体端名称" required>
					<ElInput v-model="wholePart.wholeEnd" placeholder="例如：拥有" />
				</ElFormItem>
				<ElFormItem label="部分端名称" required>
					<ElInput v-model="wholePart.partEnd" placeholder="例如：包含" />
				</ElFormItem>
			</div>
			<ElCheckbox v-model="wholePart.partIndependent">部分可独立存在</ElCheckbox>
		</div>
		<ElFormItem label="反向名称"><ElInput v-model="draft.inverse" /></ElFormItem>
	</ElForm>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import type { Cardinality, CompositionKind, GraphObject, RelationDetail } from "../../types";

const props = defineProps<{ relation: Partial<RelationDetail>; objects: GraphObject[]; creating: boolean }>();
const draft = props.relation;
const validationError = ref("");
type CardinalityPreset = "1:1" | "1:n" | "n:1" | "m:n";
const relationKinds: { value: CompositionKind; label: string; description: string }[] = [
	{ value: "Association", label: "关联", description: "对象之间存在业务联系，各自独立，没有整体与部分的从属关系。" },
	{ value: "Aggregation", label: "聚合", description: "整体由部分构成，部分可以脱离整体独立存在。" },
	{ value: "Composition", label: "组合", description: "整体由部分构成，部分依赖整体，不能脱离整体独立存在。" }
];
const cardinalityPreset = computed<CardinalityPreset | undefined>({
	get: () => {
		const source = draft.sourceCardinality;
		const target = draft.targetCardinality;
		if (!validCardinality(source) || !validCardinality(target)) return undefined;
		const sourceMany = source.max === "N" || source.max > 1;
		const targetMany = target.max === "N" || target.max > 1;
		if (!sourceMany && !targetMany) return "1:1";
		if (!sourceMany && targetMany) return "1:n";
		if (sourceMany && !targetMany) return "n:1";
		if (sourceMany && targetMany) return "m:n";
		return undefined;
	},
	set: value => {
		if (!value) return;
		const sourceMax = value === "n:1" || value === "m:n" ? "N" : 1;
		const targetMax = value === "1:n" || value === "m:n" ? "N" : 1;
		draft.sourceCardinality = presetCardinality(draft.sourceCardinality, sourceMax);
		draft.targetCardinality = presetCardinality(draft.targetCardinality, targetMax);
		validationError.value = "";
	}
});
const targetId = computed({
	get: () => (Array.isArray(draft.targetType) ? draft.targetType[0] : draft.targetType),
	set: value => {
		draft.targetType = value;
	}
});
const wholePart = computed({
	get: () => draft.wholePart ?? { wholeEnd: "", partEnd: "", partIndependent: false },
	set: value => {
		draft.wholePart = value;
	}
});
const crossDomainMismatch = computed(() => {
	const source = props.objects.find(item => item.id === draft.sourceType);
	const targets = Array.isArray(draft.targetType) ? draft.targetType : [draft.targetType];
	return Boolean(source && props.objects.some(item => targets.includes(item.id) && source.domainId !== item.domainId));
});

if (draft.compositionKind !== "Association" && !draft.wholePart) {
	draft.wholePart = { wholeEnd: "", partEnd: "", partIndependent: false };
}

function validCardinality(value: Cardinality | undefined): value is Cardinality {
	return Boolean(
		value &&
		Number.isInteger(value.min) &&
		value.min >= 0 &&
		(value.max === "N" || (Number.isInteger(value.max) && value.max >= value.min))
	);
}
function presetCardinality(current: Cardinality | undefined, max: Cardinality["max"]): Cardinality {
	const min = validCardinality(current) ? current.min : 0;
	return { min: max === "N" ? min : Math.min(min, max), max };
}
function compositionChanged(value: string) {
	if (value !== "Association" && !draft.wholePart) draft.wholePart = { wholeEnd: "", partEnd: "", partIndependent: false };
}
function validate(): string | null {
	validationError.value = "";
	if (!draft.name?.trim()) return (validationError.value = "关系名称为必填项");
	if (!draft.sourceType) return (validationError.value = "请选择源对象");
	if (!targetId.value) return (validationError.value = "请选择目标对象");
	if (!validCardinality(draft.sourceCardinality) || !validCardinality(draft.targetCardinality))
		return (validationError.value = "请选择有效基数：1:1、1:n、n:1 或 m:n");
	if (draft.compositionKind !== "Association" && (!wholePart.value.wholeEnd.trim() || !wholePart.value.partEnd.trim()))
		return (validationError.value = "聚合或组合关系需要填写整体端和部分端名称");
	draft.crossDomainDeclared = crossDomainMismatch.value;
	return null;
}

defineExpose({ validate });
</script>

<style scoped>
.relation-editor {
	padding: 4px 0;
}
.endpoint-grid {
	display: grid;
	grid-template-columns: 1fr 1fr;
	gap: 12px;
}
.whole-part-block {
	padding: 12px;
	margin-bottom: 16px;
	border: 1px solid var(--ctff-border-color-grid);
	border-radius: 6px;
	background: var(--ctff-brand-color-background);
}
.relation-editor :deep(.el-alert) {
	margin-bottom: 16px;
}
.cardinality-select {
	width: 100%;
}
.field-hint {
	width: 100%;
	margin-top: 6px;
	color: var(--ctff-text-color-secondary, var(--el-text-color-secondary));
	font-size: 12px;
	line-height: 1.6;
}
.relation-type-options {
	display: flex;
	flex-direction: column;
	align-items: stretch;
	gap: 8px;
	width: 100%;
}
.relation-type-option {
	align-items: flex-start;
	height: auto;
	padding: 10px 12px;
	margin-right: 0;
	border: 1px solid var(--ctff-border-color-grid);
	border-radius: 6px;
	white-space: normal;
}
.relation-type-option.is-checked {
	border-color: var(--el-color-primary);
	background: var(--ctff-brand-color-background);
}
.relation-type-option :deep(.el-radio__input) {
	margin-top: 3px;
}
.relation-type-option :deep(.el-radio__label) {
	min-width: 0;
	line-height: 1.5;
}
.relation-type-label,
.relation-type-description {
	display: block;
}
.relation-type-label {
	font-weight: 500;
}
.relation-type-description {
	margin-top: 3px;
	color: var(--ctff-text-color-secondary, var(--el-text-color-secondary));
	font-size: 12px;
}
</style>
