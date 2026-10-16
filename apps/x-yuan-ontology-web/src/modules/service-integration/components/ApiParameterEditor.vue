<template>
	<div class="api-parameter-editor">
		<div v-if="location === 'header'" class="api-editor-toolbar">
			<span class="api-editor-hint">支持填写字段类型、必填规则、默认值和示例。</span>
			<div>
				<ElButton v-if="location === 'header' && !response" text @click="bulkVisible = true">批量粘贴</ElButton>
			</div>
		</div>
		<ElTable :data="modelValue" empty-text="暂无配置" class="api-parameter-table">
			<ElTableColumn :label="location === 'header' ? 'Header 名称' : '参数名'" min-width="155"
				><template #default="{ row, $index }"
					><ElInput
						:key="nameVersion"
						:model-value="row.name"
						:disabled="Boolean(row.$ref) || location === 'path' || lockedNames.includes(row.name ?? '')"
						placeholder="名称"
						aria-label="参数名"
						@change="renameParameter($index, String($event))" /></template
			></ElTableColumn>
			<ElTableColumn label="类型" width="220"
				><template #default="{ row, $index }"
					><div class="api-parameter-type-cell">
						<ElSelect
							:model-value="parameterSchema(row).type ?? ''"
							:data-api-schema-type="parameterSchema(row).type ?? ''"
							:disabled="Boolean(row.$ref || row.content) || isAdvancedSchema(parameterSchema(row))"
							aria-label="参数类型"
							@change="patch($index, { schema: changeSchemaType(parameterSchema(row), String($event)) })"
							><ElOption v-if="!parameterSchema(row).type" label="未指定" value="" /><ElOption
								v-for="type in schemaTypes"
								:key="type"
								:label="type"
								:value="type"
						/></ElSelect>
						<ElTooltip
							:content="location === 'path' ? 'Path 参数固定必填' : row.required ? '必填，点击设为可选' : '可选，点击设为必填'"
						>
							<ElButton
								text
								class="api-parameter-required"
								:class="{ 'is-required': location === 'path' || row.required }"
								:disabled="location === 'path' || Boolean(row.$ref)"
								:aria-label="`${row.name} 必填`"
								:aria-pressed="location === 'path' || Boolean(row.required)"
								@click="setRequired($index, !row.required)"
								>*</ElButton
							>
						</ElTooltip>
						<ApiParameterSettings
							:model-value="row"
							:location="location"
							:disabled="Boolean(row.$ref || row.content)"
							@update:model-value="patch($index, $event)"
						/></div></template
			></ElTableColumn>
			<ElTableColumn v-if="location === 'header' && !response" label="值来源" width="135"
				><template #default="{ row, $index }"
					><ElSelect
						:model-value="row['x-value-source'] ?? 'input'"
						:disabled="Boolean(row.$ref)"
						aria-label="Header 值来源"
						@change="patch($index, { 'x-value-source': $event })"
						><ElOption label="调用时传入" value="input" /><ElOption label="固定值" value="fixed" /></ElSelect></template
			></ElTableColumn>
			<ElTableColumn :label="location === 'header' && !response ? '固定值 / 示例值' : '示例值'" min-width="145"
				><template #default="{ row, $index }"
					><ElInput
						:model-value="
							row['x-value-source'] === 'fixed'
								? String(row['x-fixed-value'] ?? '')
								: displayValue(row.example !== undefined ? row.example : parameterSchema(row).example)
						"
						:disabled="Boolean(row.$ref)"
						:placeholder="row['x-value-source'] === 'fixed' ? '固定值' : '示例值'"
						aria-label="参数示例值"
						@update:model-value="setExample($index, $event)" /></template
			></ElTableColumn>
			<ElTableColumn label="说明" min-width="150"
				><template #default="{ row, $index }"
					><ElInput
						:model-value="row.description"
						:disabled="Boolean(row.$ref)"
						placeholder="说明"
						aria-label="参数说明"
						@update:model-value="patch($index, { description: $event })" /></template
			></ElTableColumn>
			<ElTableColumn label="操作" width="56" align="center"
				><template #default="{ row, $index }"
					><ElButton
						text
						type="danger"
						:disabled="location === 'path' || lockedNames.includes(row.name ?? '')"
						:icon="Delete"
						aria-label="删除参数"
						@click="removeParameter($index)" /></template
			></ElTableColumn>
		</ElTable>
		<ElButton v-if="location !== 'path'" text :icon="Plus" class="api-add-parameter-row" @click="addParameter"
			>添加{{ location === "header" ? " Header" : "参数" }}</ElButton
		>
		<p v-if="modelValue.some(row => row.$ref || row.content)" class="api-editor-hint">
			引用或自定义编码参数已保留，请通过高级 JSON 编辑。
		</p>
		<ElDialog
			v-model="bulkVisible"
			class="api-editor-subdialog"
			title="批量添加 Header"
			width="620px"
			append-to-body
			destroy-on-close
			:close-on-click-modal="false"
		>
			<p class="api-editor-hint">每行一个 Header，例如 X-Tenant-Id: ops。已存在或认证相关的 Header 会提示冲突。</p>
			<ElInput v-model="bulkText" type="textarea" :rows="8" placeholder="X-Tenant-Id: ops" /><ElAlert
				v-if="bulkError"
				:title="bulkError"
				type="error"
				:closable="false"
			/>
			<template #footer
				><ElButton @click="bulkVisible = false">取消</ElButton
				><ElButton type="primary" @click="applyBulk">添加</ElButton></template
			>
		</ElDialog>
	</div>
</template>

<script setup lang="ts">
import { Delete, Plus } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { ref } from "vue";

import { changeSchemaType, displayValue, inputValue, isAdvancedSchema, parameterSchema, schemaTypes } from "../api-contract";
import type { ApiParameter } from "../types";
import ApiParameterSettings from "./ApiParameterSettings.vue";

const props = withDefaults(
	defineProps<{ modelValue: ApiParameter[]; location: string; lockedNames?: string[]; response?: boolean }>(),
	{ lockedNames: () => [], response: false }
);
const emit = defineEmits<{ "update:modelValue": [value: ApiParameter[]] }>();
const bulkVisible = ref(false);
const bulkText = ref("");
const bulkError = ref("");
const nameVersion = ref(0);
function patch(index: number, fields: Partial<ApiParameter>) {
	emit(
		"update:modelValue",
		props.modelValue.map((row, i) => (i === index ? { ...row, ...fields } : row))
	);
}
function addParameter() {
	if (props.location === "path") return;
	let index = 1;
	const prefix = props.location === "header" ? "X-Header-" : "param";
	while (props.modelValue.some(row => row.name?.toLowerCase() === `${prefix}${index}`.toLowerCase())) index++;
	emit("update:modelValue", [
		...props.modelValue,
		{ name: `${prefix}${index}`, in: props.location, required: props.location === "path", schema: { type: "string" } }
	]);
}
function renameParameter(index: number, input: string) {
	if (props.location === "path") return;
	const name = input.trim();
	const compare = (value?: string) => (props.location === "header" ? value?.toLowerCase() : value);
	const reserved =
		props.location === "header" && !props.response && ["authorization", "accept", "content-type"].includes(name.toLowerCase());
	if (!name || reserved || props.modelValue.some((row, i) => i !== index && compare(row.name) === compare(name))) {
		ElMessage.warning(reserved ? "此 Header 请通过认证或内容类型配置" : name ? "参数名已存在" : "参数名不能为空");
		nameVersion.value++;
		return;
	}
	patch(index, { name });
}
function removeParameter(index: number) {
	if (props.location === "path") return;
	emit(
		"update:modelValue",
		props.modelValue.filter((_, i) => i !== index)
	);
}
function setExample(index: number, text: string) {
	const row = props.modelValue[index];
	patch(
		index,
		row["x-value-source"] === "fixed" ? { "x-fixed-value": text } : { example: inputValue(text, parameterSchema(row)) }
	);
}
function setRequired(index: number, required: boolean) {
	if (props.location === "path" || props.modelValue[index]?.$ref) return;
	patch(index, { required });
}
function applyBulk() {
	bulkError.value = "";
	const rows: ApiParameter[] = [];
	const names = new Set(props.modelValue.map(row => row.name?.toLowerCase()));
	for (const line of bulkText.value.split(/\r?\n/).filter(line => line.trim())) {
		const separator = line.indexOf(":");
		const name = line.slice(0, separator).trim();
		if (separator < 1 || !/^[!#$%&'*+.^_`|~\w-]+$/.test(name)) {
			bulkError.value = `Header 格式无效：${line}`;
			return;
		}
		if (["authorization", "content-type", "accept"].includes(name.toLowerCase())) {
			bulkError.value = `${name} 请通过认证或内容类型配置`;
			return;
		}
		if (names.has(name.toLowerCase())) {
			bulkError.value = `Header ${name} 重复`;
			return;
		}
		names.add(name.toLowerCase());
		rows.push({
			name,
			in: "header",
			schema: { type: "string" },
			"x-value-source": "fixed",
			"x-fixed-value": line.slice(separator + 1).trim()
		});
	}
	if (!rows.length) {
		bulkError.value = "请填写至少一个 Header";
		return;
	}
	emit("update:modelValue", [...props.modelValue, ...rows]);
	bulkVisible.value = false;
	bulkText.value = "";
}
</script>
