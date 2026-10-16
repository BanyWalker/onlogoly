<template>
	<div class="api-content-editor">
		<ElAlert
			v-if="single && Object.keys(modelValue).length > 1"
			title="已有多个请求体格式，请选择一种保留后保存。"
			type="warning"
			:closable="false"
		/>
		<div class="api-editor-toolbar">
			<div class="api-editor-inline">
				<span>内容类型</span>
				<ElSelect
					:model-value="activeFormat"
					placeholder="选择内容类型"
					aria-label="内容类型"
					@change="selectFormat(String($event))"
				>
					<ElOption label="无" value="" />
					<ElOption v-for="format in availableFormats" :key="format" :label="formatLabels[format] ?? format" :value="format" />
				</ElSelect>
				<ElButton v-if="!single && activeFormat" text type="danger" @click="removeFormat">移除此格式</ElButton>
			</div>
			<div v-if="activeFormat && kind !== 'text'">
				<ElButton v-if="kind === 'json'" text @click="openExample('infer')">从 JSON 示例生成结构</ElButton>
				<ElButton text @click="generateExample">生成示例</ElButton>
			</div>
		</div>
		<div v-if="pendingFormat !== null" class="api-editor-panel">
			<strong>切换为{{ pendingFormat ? (formatLabels[pendingFormat] ?? pendingFormat) : "无请求体" }}</strong>
			<ul>
				<li v-for="loss in pendingLosses" :key="loss">{{ loss }}</li>
			</ul>
			<ElButton @click="pendingFormat = null">取消</ElButton><ElButton type="primary" @click="confirmFormat">确认切换</ElButton>
		</div>
		<div v-if="!single && Object.keys(modelValue).length > 1" class="api-editor-inline api-media-formats">
			<span class="api-editor-hint">已配置格式</span>
			<ElTag
				v-for="format in Object.keys(modelValue)"
				:key="format"
				:effect="activeFormat === format ? 'dark' : 'plain'"
				@click="activeFormat = format"
				>{{ format }}</ElTag
			>
		</div>
		<template v-if="activeFormat && activeMedia">
			<template v-if="kind === 'text'">
				<p class="api-editor-hint">直接填写纯文本示例，无需 JSON 引号。字符长度等约束可在字段设置中配置。</p>
				<ApiSchemaSettings
					:model-value="activeMedia.schema ?? { type: 'string' }"
					@update:model-value="patchMedia({ schema: $event })"
				/>
				<ElInput
					:model-value="String(activeMedia.example ?? '')"
					type="textarea"
					:rows="10"
					aria-label="纯文本示例"
					@update:model-value="patchMedia({ example: $event })"
				/>
			</template>
			<template v-else>
				<ApiSchemaEditor
					:model-value="activeMedia.schema ?? {}"
					:flat="kind === 'form' || kind === 'files'"
					:allow-files="kind === 'files'"
					@update:model-value="patchMedia({ schema: $event })"
				/>
				<div class="api-editor-toolbar api-example-toolbar">
					<strong>示例</strong><ElButton text type="primary" @click="openExample('edit')">编辑示例</ElButton>
				</div>
				<pre v-if="activeMedia.example !== undefined" class="api-example-preview">{{
					JSON.stringify(activeMedia.example, null, 2)
				}}</pre>
				<p v-else class="api-editor-hint">暂无示例。可粘贴实际数据或根据字段结构生成。</p>
			</template>
			<div v-if="activeMedia['x-example-review-required']" class="api-editor-panel">
				<p>字段结构已变更，请更新示例或确认保留现有示例。</p>
				<ElButton @click="openExample('edit')">更新示例</ElButton><ElButton @click="keepExample">保留现有示例</ElButton>
				<p v-if="reviewError" class="api-editor-error-text">{{ reviewError }}</p>
			</div>
			<p v-if="activeMedia.examples" class="api-editor-hint">
				已导入的命名示例完整保留，可通过高级 JSON 编辑；应用单个示例将替换这些命名示例。
			</p>
		</template>
		<ElEmpty
			v-else
			:description="single && Object.keys(modelValue).length > 1 ? '请选择要保留的格式' : '未配置内容体'"
			:image-size="50"
		/>
		<ElDialog
			v-model="exampleVisible"
			class="api-editor-subdialog"
			:title="exampleMode === 'infer' ? '从 JSON 示例生成结构' : '编辑示例'"
			width="720px"
			append-to-body
			destroy-on-close
			:close-on-click-modal="false"
		>
			<template v-if="exampleMode === 'infer'">
				<ElRadioGroup v-model="inferenceMode"
					><ElRadio value="merge">合并字段，保留已有规则</ElRadio><ElRadio value="replace">替换全部结构</ElRadio></ElRadioGroup
				>
				<p class="api-editor-hint">
					{{
						inferenceMode === "merge"
							? "保留已有字段说明、必填、默认值与枚举；类型冲突沿用已有规则。"
							: "应用后将覆盖已有结构与校验规则。必填规则需要重新确认。"
					}}
				</p>
			</template>
			<p v-else class="api-editor-hint">
				{{ kind === "text" ? "直接填写纯文本。" : "填写 JSON 示例。生成的示例会先在此预览，应用后才会替换原示例。" }}
			</p>
			<ElInput v-model="exampleText" type="textarea" :rows="12" class="api-json-input" aria-label="内容示例" />
			<template v-if="inferredSchema"
				><p class="api-editor-hint">待应用结构预览</p>
				<pre class="api-example-preview">{{ JSON.stringify(inferredSchema, null, 2) }}</pre>
			</template>
			<ElAlert v-if="exampleError" :title="exampleError" type="error" :closable="false" />
			<template #footer
				><ElButton @click="exampleVisible = false">取消</ElButton
				><ElButton type="primary" @click="applyExample">{{
					exampleMode === "infer" ? "应用结构和示例" : "应用示例"
				}}</ElButton></template
			>
		</ElDialog>
	</div>
</template>

<script setup lang="ts">
import { ElMessage } from "element-plus";
import { computed, ref, watch } from "vue";

import {
	contentKind,
	convertContentFormat,
	exampleFromSchema,
	isObject,
	mergeSchemas,
	schemaFromExample,
	schemaSignature,
	validateSchema,
	valueMatches
} from "../api-contract";
import type { ApiMediaType } from "../types";
import ApiSchemaEditor from "./ApiSchemaEditor.vue";
import ApiSchemaSettings from "./ApiSchemaSettings.vue";

const props = withDefaults(defineProps<{ modelValue: Record<string, ApiMediaType>; single?: boolean }>(), { single: false });
const emit = defineEmits<{ "update:modelValue": [value: Record<string, ApiMediaType>] }>();
const formats = ["application/json", "application/x-www-form-urlencoded", "multipart/form-data", "text/plain"];
const formatLabels: Record<string, string> = {
	"application/json": "JSON (application/json)",
	"application/x-www-form-urlencoded": "普通表单 (urlencoded)",
	"multipart/form-data": "文件表单 (multipart)",
	"text/plain": "纯文本 (text/plain)"
};
const activeFormat = ref(
	props.single && Object.keys(props.modelValue).length > 1 ? "" : (Object.keys(props.modelValue)[0] ?? "")
);
const availableFormats = computed(() => [...new Set([...formats, ...Object.keys(props.modelValue)])]);
const activeMedia = computed(() => props.modelValue[activeFormat.value]);
const kind = computed(() => contentKind(activeFormat.value));
const pendingFormat = ref<string | null>(null);
const pendingLosses = ref<string[]>([]);
const exampleVisible = ref(false);
const exampleMode = ref("edit");
const inferenceMode = ref("merge");
const exampleText = ref("");
const exampleError = ref("");
const reviewError = ref("");
const inferredSchema = computed(() => {
	if (exampleMode.value !== "infer" || !exampleVisible.value) return null;
	try {
		const inferred = schemaFromExample(JSON.parse(exampleText.value));
		return inferenceMode.value === "merge" ? mergeSchemas(activeMedia.value?.schema ?? {}, inferred) : inferred;
	} catch {
		return null;
	}
});
watch(
	() => Object.keys(props.modelValue).join("|"),
	() => {
		if (props.single && Object.keys(props.modelValue).length > 1) activeFormat.value = "";
		else if (!props.modelValue[activeFormat.value]) activeFormat.value = Object.keys(props.modelValue)[0] ?? "";
	}
);
function conversion(format: string) {
	const sourceFormat = props.modelValue[format] ? format : activeFormat.value;
	const result = convertContentFormat(props.modelValue[sourceFormat], sourceFormat, format);
	const discarded = Object.keys(props.modelValue).filter(key => key !== sourceFormat && key !== format);
	if (discarded.length) result.losses.push(`其他格式 ${discarded.join("、")} 的配置将移除`);
	return result;
}
function selectFormat(format: string) {
	pendingFormat.value = null;
	if (props.single) {
		const result = conversion(format);
		if (result.losses.length) {
			pendingLosses.value = result.losses;
			pendingFormat.value = format;
			return;
		}
		emit("update:modelValue", format ? { [format]: result.media! } : {});
	} else if (!format) emit("update:modelValue", {});
	else if (!props.modelValue[format])
		emit("update:modelValue", { ...props.modelValue, [format]: convertContentFormat(undefined, "", format).media! });
	activeFormat.value = format;
}
function confirmFormat() {
	if (pendingFormat.value === null) return;
	const format = pendingFormat.value;
	emit("update:modelValue", format ? { [format]: conversion(format).media! } : {});
	activeFormat.value = format;
	pendingFormat.value = null;
}
function removeFormat() {
	const content = { ...props.modelValue };
	delete content[activeFormat.value];
	emit("update:modelValue", content);
}
function patchMedia(patch: Partial<ApiMediaType>) {
	const next = { ...activeMedia.value, ...patch };
	if (
		patch.schema &&
		schemaSignature(patch.schema) !== schemaSignature(activeMedia.value?.schema ?? {}) &&
		(next.example !== undefined || next.examples)
	)
		next["x-example-review-required"] = true;
	if (Object.hasOwn(patch, "example")) {
		delete next.examples;
		delete next["x-example-review-required"];
	}
	reviewError.value = "";
	emit("update:modelValue", { ...props.modelValue, [activeFormat.value]: next });
}
function openExample(mode: string) {
	exampleMode.value = mode;
	inferenceMode.value = "merge";
	exampleText.value =
		activeMedia.value?.example === undefined
			? ""
			: kind.value === "text"
				? String(activeMedia.value.example)
				: JSON.stringify(activeMedia.value.example, null, 2);
	exampleError.value = "";
	exampleVisible.value = true;
}
function applyExample() {
	try {
		const example: unknown = kind.value === "text" ? exampleText.value : JSON.parse(exampleText.value);
		const schema = inferredSchema.value ?? activeMedia.value?.schema ?? {};
		const error = validateSchema(schema);
		if (error) throw new Error(error);
		if (!valueMatches(example, schema)) throw new Error("示例不符合当前字段类型或校验规则，请调整示例或结构");
		patchMedia(exampleMode.value === "infer" ? { schema, example } : { example });
		exampleVisible.value = false;
	} catch (cause) {
		exampleError.value =
			cause instanceof SyntaxError ? "请输入有效的 JSON 示例" : cause instanceof Error ? cause.message : "示例无效";
	}
}
function keepExample() {
	const media = activeMedia.value;
	if (!media) return;
	const samples = [
		...(media.example === undefined ? [] : [media.example]),
		...Object.values(isObject(media.examples) ? media.examples : {})
			.filter(isObject)
			.filter(item => Object.hasOwn(item, "value"))
			.map(item => item.value)
	];
	if (samples.some(sample => !valueMatches(sample, media.schema ?? {}))) {
		reviewError.value = "现有示例不符合当前结构，请更新示例。";
		return;
	}
	const next = { ...media };
	delete next["x-example-review-required"];
	emit("update:modelValue", { ...props.modelValue, [activeFormat.value]: next });
	reviewError.value = "";
}
function generateExample() {
	const example = exampleFromSchema(activeMedia.value?.schema ?? {});
	if (example === undefined || !valueMatches(example, activeMedia.value?.schema ?? {})) {
		ElMessage.warning("当前结构无法自动生成示例，请手工填写");
		return;
	}
	openExample("edit");
	exampleText.value = JSON.stringify(example, null, 2);
}
</script>
