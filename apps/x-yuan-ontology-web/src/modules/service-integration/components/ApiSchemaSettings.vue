<template>
	<ElForm label-position="top" class="api-schema-settings">
		<div class="api-form-grid">
			<ElFormItem label="默认值"
				><ElInput
					:model-value="displayValue(modelValue.default)"
					placeholder="按类型填写，对象或数组使用 JSON"
					@update:model-value="set('default', inputValue($event, modelValue))"
			/></ElFormItem>
			<ElFormItem label="枚举值"
				><ElSelect
					:model-value="(modelValue.enum ?? []).map(displayValue)"
					multiple
					filterable
					allow-create
					default-first-option
					placeholder="输入取值后按回车添加"
					@update:model-value="
						set('enum', $event.length ? $event.map((value: string) => inputValue(value, modelValue)) : undefined)
					"
					><ElOption
						v-for="value in modelValue.enum ?? []"
						:key="displayValue(value)"
						:label="displayValue(value)"
						:value="displayValue(value)" /></ElSelect
			></ElFormItem>
		</div>
		<div v-if="modelValue.type === 'string' && !isFileSchema(modelValue)" class="api-form-grid">
			<ElFormItem label="格式"
				><ElSelect
					:model-value="modelValue.format"
					clearable
					filterable
					allow-create
					placeholder="可选，例如 date-time"
					@update:model-value="set('format', $event || undefined)"
					><ElOption
						v-for="format in ['date', 'date-time', 'email', 'uri', 'uuid', 'hostname', 'ipv4', 'ipv6']"
						:key="format"
						:label="format"
						:value="format" /></ElSelect
			></ElFormItem>
			<ElFormItem label="正则表达式"
				><ElInput
					:model-value="String(modelValue.pattern ?? '')"
					placeholder="例如 ^[A-Z0-9-]+$"
					@update:model-value="set('pattern', $event || undefined)"
			/></ElFormItem>
		</div>
		<div v-if="bounds" class="api-form-grid">
			<ElFormItem :label="bounds[2]"
				><ElInputNumber
					:model-value="numberValue(bounds[0])"
					:min="modelValue.type === 'number' || modelValue.type === 'integer' ? undefined : 0"
					:precision="modelValue.type === 'number' ? undefined : 0"
					controls-position="right"
					@change="set(bounds![0], $event)"
			/></ElFormItem>
			<ElFormItem :label="bounds[3]"
				><ElInputNumber
					:model-value="numberValue(bounds[1])"
					:min="modelValue.type === 'number' || modelValue.type === 'integer' ? undefined : 0"
					:precision="modelValue.type === 'number' ? undefined : 0"
					controls-position="right"
					@change="set(bounds![1], $event)"
			/></ElFormItem>
		</div>
		<ElCheckbox v-if="!hideNullable" :model-value="modelValue.nullable ?? false" @change="set('nullable', Boolean($event))"
			>允许 null</ElCheckbox
		>
		<p v-if="error" class="api-editor-error-text">{{ error }}</p>
	</ElForm>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { displayValue, inputValue, isFileSchema, validateSchema } from "../api-contract";
import type { ApiSchema } from "../types";

const props = defineProps<{ modelValue: ApiSchema; hideNullable?: boolean }>();
const emit = defineEmits<{ "update:modelValue": [value: ApiSchema] }>();
const bounds = computed(() => {
	if (["integer", "number"].includes(props.modelValue.type ?? "")) return ["minimum", "maximum", "最小值", "最大值"];
	if (props.modelValue.type === "string") return ["minLength", "maxLength", "最小长度", "最大长度"];
	if (props.modelValue.type === "array") return ["minItems", "maxItems", "最少元素数", "最多元素数"];
	return null;
});
const error = computed(() => validateSchema(props.modelValue));
function numberValue(key: string) {
	return typeof props.modelValue[key] === "number" ? (props.modelValue[key] as number) : undefined;
}
function set(key: string, value: unknown) {
	const schema = { ...props.modelValue };
	if (value === undefined || value === null) delete schema[key];
	else schema[key] = value;
	emit("update:modelValue", schema);
}
</script>
