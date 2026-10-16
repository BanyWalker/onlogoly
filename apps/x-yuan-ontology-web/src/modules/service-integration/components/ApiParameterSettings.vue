<template>
	<ElPopover
		v-model:visible="visible"
		trigger="click"
		placement="right-start"
		:width="480"
		:hide-after="0"
		:popper-options="{
			strategy: 'fixed',
			modifiers: [{ name: 'preventOverflow', options: { tether: false, altAxis: true, padding: 12 } }]
		}"
		:persistent="false"
		:disabled="disabled"
		popper-class="integration-dialog api-parameter-settings-popover"
	>
		<template #reference>
			<span class="api-parameter-settings-trigger">
				<ElTooltip content="高级设置" :disabled="visible || disabled" :show-after="300">
					<ElButton
						text
						:icon="SetUp"
						:disabled="disabled"
						class="api-parameter-settings-button"
						:class="{ 'is-active': visible }"
						:aria-label="`${modelValue.name || '参数'} 高级设置`"
						:aria-expanded="visible"
						aria-haspopup="dialog"
					/>
				</ElTooltip>
			</span>
		</template>
		<div
			class="api-parameter-settings-panel"
			role="dialog"
			:aria-label="`${modelValue.name || '参数'} 高级设置`"
			@keydown.esc.stop.prevent="visible = false"
		>
			<div class="api-parameter-settings-heading">
				<ElRadioGroup v-model="mode" size="small" aria-label="参数配置方式">
					<ElRadioButton value="type">数据类型</ElRadioButton><ElRadioButton value="json">JSON Schema</ElRadioButton>
				</ElRadioGroup>
				<ElButton text :icon="Close" aria-label="关闭高级设置" @click="visible = false" />
			</div>
			<template v-if="mode === 'type'">
				<ElSelect
					:model-value="schema.type ?? ''"
					:data-api-schema-type="schema.type ?? ''"
					:disabled="isAdvancedSchema(schema)"
					aria-label="参数数据类型"
					class="api-parameter-settings-type"
					@change="setType(String($event))"
				>
					<ElOption v-if="!schema.type" label="未指定" value="" />
					<ElOption v-for="type in schemaTypes" :key="type" :label="type" :value="type" />
				</ElSelect>
				<div class="api-parameter-settings-flags">
					<label
						><span>必需</span
						><ElSwitch
							:model-value="location === 'path' || Boolean(modelValue.required)"
							:disabled="location === 'path'"
							aria-label="参数必需"
							size="small"
							@change="setRequired(Boolean($event))"
					/></label>
					<label
						><span>允许 NULL</span
						><ElSwitch
							:model-value="schema.nullable ?? false"
							:disabled="isAdvancedSchema(schema)"
							aria-label="参数允许 NULL"
							size="small"
							@change="schema = { ...schema, nullable: Boolean($event) }"
					/></label>
				</div>
				<ElAlert v-if="isAdvancedSchema(schema)" title="引用或组合结构请在 JSON Schema 中编辑。" type="info" :closable="false" />
				<template v-else>
					<ApiSchemaSettings v-model="schema" hide-nullable />
					<p v-if="['object', 'array'].includes(schema.type ?? '')" class="api-editor-hint">
						嵌套字段与数组元素可在 JSON Schema 中配置。
					</p>
				</template>
			</template>
			<template v-else>
				<ElInput
					v-model="schemaText"
					type="textarea"
					:rows="12"
					class="api-json-input"
					aria-label="参数 JSON Schema"
					spellcheck="false"
				/>
				<p v-if="jsonError" class="api-editor-error-text" role="alert">{{ jsonError }}</p>
				<div class="api-parameter-json-actions">
					<ElButton type="primary" size="small" @click="applyJson">应用 JSON Schema</ElButton>
				</div>
			</template>
		</div>
	</ElPopover>
</template>

<script setup lang="ts">
import { Close, SetUp } from "@element-plus/icons-vue";
import { computed, ref, watch } from "vue";
import { changeSchemaType, isAdvancedSchema, parameterSchema, schemaTypes, validateSchema } from "../api-contract";
import type { ApiParameter, ApiSchema } from "../types";
import ApiSchemaSettings from "./ApiSchemaSettings.vue";

const props = defineProps<{ modelValue: ApiParameter; location: string; disabled?: boolean }>();
const emit = defineEmits<{ "update:modelValue": [value: ApiParameter] }>();
const visible = ref(false);
const mode = ref("type");
const schemaText = ref("");
const jsonError = ref("");
const schema = computed<ApiSchema>({
	get: () => parameterSchema(props.modelValue),
	set: value => {
		if (!props.disabled) emit("update:modelValue", { ...props.modelValue, schema: value });
	}
});
watch(mode, value => {
	if (value === "json") {
		schemaText.value = JSON.stringify(schema.value, null, 2);
		jsonError.value = "";
	}
});
watch(visible, value => {
	if (value) resetView();
});
function resetView() {
	mode.value = isAdvancedSchema(schema.value) ? "json" : "type";
	schemaText.value = JSON.stringify(schema.value, null, 2);
	jsonError.value = "";
}
function setRequired(required: boolean) {
	if (props.disabled || props.location === "path") return;
	emit("update:modelValue", { ...props.modelValue, required });
}
function setType(type: string) {
	if (isAdvancedSchema(schema.value)) return;
	schema.value = changeSchemaType(schema.value, type);
}
function applyJson() {
	try {
		const next: unknown = JSON.parse(schemaText.value);
		const issue = validateSchema(next);
		if (issue) throw new Error(issue);
		schema.value = next as ApiSchema;
		jsonError.value = "";
		mode.value = "type";
	} catch (cause) {
		jsonError.value = cause instanceof Error ? cause.message : "请输入有效的 JSON Schema";
	}
}
</script>
