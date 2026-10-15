<template>
	<div class="api-content-editor">
		<div class="api-editor-toolbar">
			<div class="api-editor-inline">
				<span>内容类型</span
				><ElSelect :model-value="activeFormat" aria-label="内容类型" @change="selectFormat(String($event))"
					><ElOption label="无" value="" /><ElOption
						v-for="format in availableFormats"
						:key="format"
						:label="format"
						:value="format" /></ElSelect
				><ElButton v-if="activeFormat" text type="danger" @click="removeFormat">移除此格式</ElButton>
			</div>
			<div v-if="activeFormat">
				<ElButton text @click="openExample('infer')">从 JSON 示例生成结构</ElButton
				><ElButton text @click="generateExample">生成示例</ElButton>
			</div>
		</div>
		<div v-if="Object.keys(modelValue).length > 1" class="api-editor-inline api-media-formats">
			<span class="api-editor-hint">已配置格式</span
			><ElTag
				v-for="format in Object.keys(modelValue)"
				:key="format"
				:effect="activeFormat === format ? 'dark' : 'plain'"
				@click="activeFormat = format"
				>{{ format }}</ElTag
			>
		</div>
		<template v-if="activeFormat && activeMedia">
			<ApiSchemaEditor :model-value="activeMedia.schema ?? {}" @update:model-value="patchMedia({ schema: $event })" />
			<div class="api-editor-toolbar api-example-toolbar">
				<strong>示例</strong><ElButton text type="primary" @click="openExample('edit')">编辑示例</ElButton>
			</div>
			<pre v-if="activeMedia.example !== undefined" class="api-example-preview">{{
				JSON.stringify(activeMedia.example, null, 2)
			}}</pre>
			<p v-else class="api-editor-hint">暂无示例。可粘贴实际数据或根据字段结构生成。</p>
			<p v-if="activeMedia.examples" class="api-editor-hint">已导入的多个命名示例完整保留，可通过高级 JSON 编辑。</p>
		</template>
		<ElEmpty v-else description="未配置内容体" :image-size="50" />
		<ElDialog
			v-model="exampleVisible"
			:title="exampleMode === 'infer' ? '从 JSON 示例生成结构' : '编辑示例'"
			width="720px"
			append-to-body
			destroy-on-close
		>
			<p class="api-editor-hint">
				{{
					exampleMode === "infer"
						? "生成字段类型和层级，保留示例；必填规则请根据接口约定确认。"
						: "JSON 格式填写，例如对象、数组、数字，文本示例请使用双引号。"
				}}
			</p>
			<ElInput v-model="exampleText" type="textarea" :rows="15" class="api-json-input" aria-label="内容示例 JSON" />
			<ElAlert v-if="exampleError" :title="exampleError" type="error" :closable="false" />
			<template #footer
				><ElButton @click="exampleVisible = false">取消</ElButton
				><ElButton type="primary" @click="applyExample">{{
					exampleMode === "infer" ? "生成并应用" : "应用示例"
				}}</ElButton></template
			>
		</ElDialog>
	</div>
</template>

<script setup lang="ts">
import { ElMessage } from "element-plus";
import { computed, ref, watch } from "vue";

import { exampleFromSchema, schemaFromExample } from "../api-contract";
import type { ApiMediaType } from "../types";
import ApiSchemaEditor from "./ApiSchemaEditor.vue";

const props = defineProps<{ modelValue: Record<string, ApiMediaType> }>();
const emit = defineEmits<{ "update:modelValue": [value: Record<string, ApiMediaType>] }>();
const formats = ["application/json", "application/x-www-form-urlencoded", "multipart/form-data", "text/plain"];
const activeFormat = ref(Object.keys(props.modelValue)[0] ?? "");
const availableFormats = computed(() => [...new Set([...formats, ...Object.keys(props.modelValue)])]);
const activeMedia = computed(() => props.modelValue[activeFormat.value]);
const exampleVisible = ref(false);
const exampleMode = ref("edit");
const exampleText = ref("");
const exampleError = ref("");
watch(
	() => Object.keys(props.modelValue).join("|"),
	() => {
		if (!props.modelValue[activeFormat.value]) activeFormat.value = Object.keys(props.modelValue)[0] ?? "";
	}
);
function selectFormat(format: string) {
	activeFormat.value = format;
	if (!format) {
		emit("update:modelValue", {});
		return;
	}
	if (!props.modelValue[format])
		emit("update:modelValue", {
			...props.modelValue,
			[format]: { schema: format === "text/plain" ? { type: "string" } : { type: "object", properties: {} } }
		});
}
function removeFormat() {
	const content = { ...props.modelValue };
	delete content[activeFormat.value];
	emit("update:modelValue", content);
}
function patchMedia(patch: Partial<ApiMediaType>) {
	const next = { ...activeMedia.value, ...patch };
	if (Object.hasOwn(patch, "example")) delete next.examples;
	emit("update:modelValue", { ...props.modelValue, [activeFormat.value]: next });
}
function openExample(mode: string) {
	exampleMode.value = mode;
	exampleText.value = activeMedia.value?.example === undefined ? "" : JSON.stringify(activeMedia.value.example, null, 2);
	exampleError.value = "";
	exampleVisible.value = true;
}
function applyExample() {
	try {
		const example: unknown = JSON.parse(exampleText.value);
		patchMedia(exampleMode.value === "infer" ? { schema: schemaFromExample(example), example } : { example });
		exampleVisible.value = false;
	} catch {
		exampleError.value = "请输入有效的 JSON 示例";
	}
}
function generateExample() {
	const example = exampleFromSchema(activeMedia.value?.schema ?? {});
	if (example === undefined) {
		ElMessage.warning("当前结构无法自动生成示例，请手工填写");
		return;
	}
	patchMedia({ example });
}
</script>
