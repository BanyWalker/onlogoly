<template>
	<div class="fact-property-field">
		<div class="property-label">
			<span>{{ attribute.name }} <span v-if="attribute.required || identity" class="required-mark">*</span></span>
			<ElTag v-if="identity" size="small" effect="plain">业务标识</ElTag>
			<ElTag v-if="attribute.readOnlyNature" size="small" type="info" effect="plain">{{
				creating ? "创建后只读" : "只读"
			}}</ElTag>
			<span class="property-type">{{ attribute.dataType }}{{ multiple(attribute) ? " · 多值" : "" }}</span>
		</div>
		<template v-if="!editing">
			<div class="property-value" :class="{ 'is-null': nullValue(modelValue) || modelValue === undefined }">
				{{ displayValue(modelValue) }}
			</div>
		</template>
		<template v-else>
			<div v-if="attribute.nullable" class="null-choice">
				<ElSelect
					:append-to="overlayTarget"
					:model-value="nullValue(modelValue) ? 'null' : 'value'"
					size="small"
					:disabled="locked"
					@update:model-value="chooseNull"
				>
					<ElOption label="填写值" value="value" />
					<ElOption :label="nullMeaningLabels[attribute.nullMeaning ?? ''] ?? '空值'" value="null" />
				</ElSelect>
			</div>
			<div v-if="nullValue(modelValue)" class="null-explanation">
				{{ displayValue(modelValue) }}{{ attribute.valueSemantics ? ` · ${attribute.valueSemantics}` : "" }}
			</div>
			<ElSelect
				v-else-if="multiple(attribute)"
				:append-to="overlayTarget"
				:model-value="Array.isArray(modelValue) ? modelValue.map(String) : []"
				multiple
				filterable
				:allow-create="attribute.dataType !== 'Enumeration' && attribute.dataType !== 'Boolean'"
				default-first-option
				:disabled="locked"
				placeholder="选择多个值，或输入后按回车添加"
				@update:model-value="update"
			>
				<ElOption
					v-for="choice in attribute.allowedValues ?? []"
					:key="choice.code"
					:value="choice.code"
					:label="choice.meaning ? `${choice.code} · ${choice.meaning}` : choice.code"
				/>
				<template v-if="attribute.dataType === 'Boolean'"
					><ElOption label="是" value="true" /><ElOption label="否" value="false"
				/></template>
			</ElSelect>
			<ElSelect
				v-else-if="attribute.dataType === 'Enumeration'"
				:append-to="overlayTarget"
				:model-value="modelValue"
				:disabled="locked"
				clearable
				filterable
				placeholder="请选择枚举值"
				@update:model-value="update"
			>
				<ElOption
					v-for="choice in attribute.allowedValues ?? []"
					:key="choice.code"
					:value="choice.code"
					:label="`${choice.code}${choice.meaning ? ` · ${choice.meaning}` : ''}${choice.deprecated ? '（已废弃）' : ''}`"
				/>
			</ElSelect>
			<ElSelect
				v-else-if="attribute.dataType === 'Boolean'"
				:append-to="overlayTarget"
				:model-value="modelValue"
				:disabled="locked"
				clearable
				placeholder="请选择"
				@update:model-value="update"
			>
				<ElOption label="是" :value="true" /><ElOption label="否" :value="false" />
			</ElSelect>
			<ElInputNumber
				v-else-if="attribute.dataType === 'Integer' || attribute.dataType === 'Decimal'"
				:model-value="typeof modelValue === 'number' ? modelValue : undefined"
				:precision="attribute.dataType === 'Integer' ? 0 : undefined"
				:controls="false"
				:disabled="locked"
				@update:model-value="update"
			/>
			<ElInput
				v-else
				:model-value="String(modelValue ?? '')"
				:disabled="locked"
				:placeholder="attribute.dataType === 'DateTime' ? '2026-10-15T10:00:00+08:00' : '请输入属性值'"
				@update:model-value="update"
			/>
		</template>
		<div v-if="error" class="field-error" role="alert">{{ error }}</div>
		<div v-else-if="attribute.readOnlyNature || attribute.valueSemantics" class="field-help">
			{{ attribute.readOnlyNature || attribute.valueSemantics }}
		</div>
	</div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { AttributeDef } from "@/modules/object-model/types";
import { displayValue, multiple, nullMeaningLabels, nullValue } from "../form";
import { useFactOverlay } from "../fullscreen";

const overlayTarget = useFactOverlay();

const props = defineProps<{
	attribute: AttributeDef;
	modelValue?: unknown;
	editing: boolean;
	creating: boolean;
	identity?: boolean;
	error?: string;
}>();
const emit = defineEmits<{ (event: "update:modelValue", value: unknown): void }>();
const locked = computed(() => !props.creating && !!props.attribute.readOnlyNature);
function update(value: unknown) {
	emit("update:modelValue", value === "" && props.attribute.dataType !== "String" ? undefined : value);
}
function chooseNull(value: string) {
	if (value === "null")
		emit("update:modelValue", props.attribute.nullMeaning ? { value: null, nullReason: props.attribute.nullMeaning } : null);
	else emit("update:modelValue", undefined);
}
</script>

<style scoped>
.fact-property-field {
	padding: 12px 0;
	border-bottom: 1px solid var(--el-border-color-lighter);
}

.property-label {
	display: flex;
	gap: 7px;
	align-items: center;
	margin-bottom: 8px;
	font-size: 13px;
	color: var(--el-text-color-primary);
}

.required-mark,
.field-error {
	color: var(--el-color-danger);
}

.property-type {
	margin-left: auto;
	font-size: 11px;
	color: var(--el-text-color-placeholder);
}

.property-value {
	font-size: 13px;
	line-height: 1.7;
	color: var(--el-text-color-regular);
	overflow-wrap: anywhere;
	white-space: pre-wrap;
}

.is-null,
.field-help,
.null-explanation {
	color: var(--el-text-color-secondary);
}

.field-help,
.field-error {
	margin-top: 6px;
	font-size: 12px;
}

.null-choice {
	width: 112px;
	margin-bottom: 6px;
}

.null-explanation {
	padding: 8px 0;
	font-size: 13px;
}

.el-select,
.el-input-number {
	width: 100%;
}
</style>
