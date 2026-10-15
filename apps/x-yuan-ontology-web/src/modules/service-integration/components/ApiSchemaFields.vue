<template>
	<div class="api-schema-fields">
		<template v-if="modelValue.type === 'object'">
			<template v-for="(field, name) in modelValue.properties ?? {}" :key="`${name}-${renameVersion}`">
				<div class="api-schema-row">
					<div class="api-schema-name" :style="{ paddingLeft: `${depth * 16}px` }">
						<ElInput
							:model-value="name"
							placeholder="字段名"
							:aria-label="`字段名 ${name}`"
							@change="renameField(String(name), String($event))"
						/>
					</div>
					<ElSelect
						:model-value="field.type ?? ''"
						:disabled="isAdvancedSchema(field)"
						:aria-label="`${name} 类型`"
						@change="updateField(String(name), changeSchemaType(field, String($event)))"
						><ElOption v-if="!field.type" label="未指定" value="" /><ElOption
							v-for="type in schemaTypes"
							:key="type"
							:label="type"
							:value="type"
					/></ElSelect>
					<ElCheckbox
						:model-value="modelValue.required?.includes(String(name)) ?? false"
						:aria-label="`${name} 必填`"
						@change="setRequired(String(name), Boolean($event))"
					/>
					<ElInput
						:model-value="field.description"
						placeholder="说明"
						:aria-label="`${name} 说明`"
						@update:model-value="updateField(String(name), { ...field, description: $event })"
					/>
					<ElInput
						:model-value="displayValue(field.example)"
						placeholder="示例值"
						:aria-label="`${name} 示例值`"
						@update:model-value="updateField(String(name), { ...field, example: inputValue($event, field) })"
					/>
					<ElButton text type="danger" :icon="Delete" :aria-label="`删除字段 ${name}`" @click="removeField(String(name))" />
				</div>
				<div v-if="isAdvancedSchema(field)" class="api-editor-hint" :style="{ paddingLeft: `${(depth + 1) * 16}px` }">
					引用或组合结构已保留，可通过“高级结构”编辑。
				</div>
				<ApiSchemaFields
					v-else-if="field.type === 'object' || field.type === 'array'"
					:model-value="field"
					:depth="depth + 1"
					@update:model-value="updateField(String(name), $event)"
				/>
			</template>
			<ElButton text type="primary" :icon="Plus" :style="{ marginLeft: `${depth * 16}px` }" @click="addField">{{
				depth ? "新增子字段" : "新增字段"
			}}</ElButton>
		</template>
		<template v-else-if="modelValue.type === 'array'">
			<div class="api-array-item" :style="{ paddingLeft: `${depth * 16}px` }">
				<span>数组元素类型</span>
				<ElSelect
					:model-value="modelValue.items?.type ?? ''"
					:disabled="isAdvancedSchema(modelValue.items ?? {})"
					aria-label="数组元素类型"
					@change="updateItems(changeSchemaType(modelValue.items ?? {}, String($event)))"
					><ElOption v-if="!modelValue.items?.type" label="未指定" value="" /><ElOption
						v-for="type in schemaTypes"
						:key="type"
						:label="type"
						:value="type"
				/></ElSelect>
				<span v-if="isAdvancedSchema(modelValue.items ?? {})" class="api-editor-hint">组合结构已保留</span>
			</div>
			<ApiSchemaFields
				v-if="modelValue.items && !isAdvancedSchema(modelValue.items)"
				:model-value="modelValue.items"
				:depth="depth + 1"
				@update:model-value="updateItems"
			/>
		</template>
	</div>
</template>

<script setup lang="ts">
import { Delete, Plus } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { ref } from "vue";

import { changeSchemaType, displayValue, inputValue, isAdvancedSchema, schemaTypes } from "../api-contract";
import type { ApiSchema } from "../types";

const props = withDefaults(defineProps<{ modelValue: ApiSchema; depth?: number }>(), { depth: 0 });
const emit = defineEmits<{ "update:modelValue": [value: ApiSchema] }>();
const renameVersion = ref(0);

function updateField(name: string, field: ApiSchema) {
	emit("update:modelValue", { ...props.modelValue, properties: { ...props.modelValue.properties, [name]: field } });
}
function addField() {
	let index = 1;
	while (props.modelValue.properties?.[`field${index}`]) index++;
	updateField(`field${index}`, { type: "string" });
}
function renameField(previous: string, input: string) {
	const next = input.trim();
	if (previous === next) return;
	if (!next || Object.hasOwn(props.modelValue.properties ?? {}, next)) {
		ElMessage.warning(next ? "字段名已存在" : "字段名不能为空");
		renameVersion.value++;
		return;
	}
	emit("update:modelValue", {
		...props.modelValue,
		properties: Object.fromEntries(
			Object.entries(props.modelValue.properties ?? {}).map(([name, field]) => [name === previous ? next : name, field])
		),
		required: props.modelValue.required?.map(name => (name === previous ? next : name))
	});
}
function removeField(name: string) {
	const properties = { ...props.modelValue.properties };
	delete properties[name];
	emit("update:modelValue", {
		...props.modelValue,
		properties,
		required: props.modelValue.required?.filter(item => item !== name)
	});
}
function setRequired(name: string, required: boolean) {
	const names = new Set(props.modelValue.required ?? []);
	if (required) names.add(name);
	else names.delete(name);
	emit("update:modelValue", { ...props.modelValue, required: [...names] });
}
function updateItems(items: ApiSchema) {
	emit("update:modelValue", { ...props.modelValue, items });
}
</script>
