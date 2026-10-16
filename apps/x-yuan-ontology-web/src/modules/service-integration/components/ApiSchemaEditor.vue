<template>
	<div class="api-schema-editor">
		<div class="api-editor-toolbar">
			<div v-if="!flat" class="api-editor-inline">
				<span>根节点类型</span
				><ElSelect
					:model-value="modelValue.type ?? ''"
					:data-api-schema-type="modelValue.type ?? ''"
					:disabled="isAdvancedSchema(modelValue)"
					aria-label="根节点类型"
					@change="emit('update:modelValue', changeSchemaType(modelValue, String($event)))"
					><ElOption v-if="!modelValue.type" label="未指定" value="" /><ElOption
						v-for="type in schemaTypes"
						:key="type"
						:label="type"
						:value="type"
				/></ElSelect>
			</div>
			<span v-else class="api-editor-hint">{{
				allowFiles
					? "文件表单支持普通字段、文件与多文件；文件以 binary 定义。"
					: "普通表单按字段填写，支持字符串、数字、整数和布尔值。"
			}}</span>
			<div>
				<ElButton v-if="!isAdvancedSchema(modelValue)" text @click="settingsVisible = !settingsVisible">根节点设置</ElButton
				><ElButton text type="primary" @click="openAdvanced">高级结构</ElButton>
			</div>
		</div>
		<ElAlert
			v-if="isAdvancedSchema(modelValue)"
			title="此结构包含引用或组合定义，原始内容已保留，请通过高级结构编辑。"
			type="info"
			:closable="false"
		/>
		<ApiSchemaSettings
			v-if="settingsVisible && !isAdvancedSchema(modelValue)"
			:model-value="modelValue"
			@update:model-value="emit('update:modelValue', $event)"
		/>
		<div v-if="!isAdvancedSchema(modelValue)" class="api-schema-table">
			<div v-if="modelValue.type === 'object' || modelValue.type === 'array'" class="api-schema-row api-table-heading">
				<span>字段名</span><span>类型</span><span>必填</span><span>说明</span><span>示例值</span><span>操作</span>
			</div>
			<ApiSchemaFields
				:model-value="modelValue"
				:flat="flat"
				:allow-files="allowFiles"
				@update:model-value="emit('update:modelValue', $event)"
			/>
			<ApiSchemaSettings
				v-if="!settingsVisible && !['object', 'array'].includes(modelValue.type ?? '')"
				:model-value="modelValue"
				@update:model-value="emit('update:modelValue', $event)"
			/>
		</div>
		<ElDialog
			v-model="advancedVisible"
			class="api-editor-subdialog"
			title="编辑字段结构"
			width="720px"
			append-to-body
			destroy-on-close
			:close-on-click-modal="false"
		>
			<p class="api-editor-hint">支持格式、默认值、枚举、取值范围和引用等结构配置。</p>
			<ElInput v-model="advancedText" type="textarea" :rows="16" class="api-json-input" aria-label="字段结构 JSON" />
			<ElAlert v-if="advancedError" :title="advancedError" type="error" :closable="false" />
			<template #footer
				><ElButton @click="advancedVisible = false">取消</ElButton
				><ElButton type="primary" @click="applyAdvanced">应用</ElButton></template
			>
		</ElDialog>
	</div>
</template>

<script setup lang="ts">
import { ref } from "vue";

import { changeSchemaType, isAdvancedSchema, schemaTypes, validateSchema } from "../api-contract";
import type { ApiSchema } from "../types";
import ApiSchemaFields from "./ApiSchemaFields.vue";
import ApiSchemaSettings from "./ApiSchemaSettings.vue";

const props = withDefaults(defineProps<{ modelValue: ApiSchema; flat?: boolean; allowFiles?: boolean }>(), {
	flat: false,
	allowFiles: false
});
const emit = defineEmits<{ "update:modelValue": [value: ApiSchema] }>();
const settingsVisible = ref(false);
const advancedVisible = ref(false);
const advancedText = ref("");
const advancedError = ref("");
function openAdvanced() {
	advancedText.value = JSON.stringify(props.modelValue, null, 2);
	advancedError.value = "";
	advancedVisible.value = true;
}
function applyAdvanced() {
	try {
		const schema: unknown = JSON.parse(advancedText.value);
		const error = validateSchema(schema);
		if (error) throw new Error(error);
		if (props.flat && (schema as ApiSchema).type !== "object") throw new Error("表单根节点必须为 object");
		emit("update:modelValue", schema as ApiSchema);
		advancedVisible.value = false;
	} catch (cause) {
		advancedError.value = cause instanceof Error ? cause.message : "请输入有效的字段结构";
	}
}
</script>
