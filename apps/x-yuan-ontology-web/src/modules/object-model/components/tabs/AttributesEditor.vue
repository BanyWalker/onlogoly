<template>
	<div class="attributes-editor">
		<template v-if="!editingAttribute">
			<div class="section-bar">
				<strong>属性清单 · {{ object.attributes.length }}</strong>
				<ElButton size="small" type="primary" plain @click="addAttribute">新增属性</ElButton>
			</div>
			<p class="hint">这里预览对象类型的全部属性；点击任意一行进入配置。修改先保留在对象草稿中，点击底部“保存”统一提交。</p>
			<ElAlert v-if="feedback" class="editor-feedback" :title="feedback" type="success" :closable="true" show-icon @close="feedback = ''" />
			<ElEmpty v-if="object.attributes.length === 0" description="暂无属性，点击“新增属性”开始定义" :image-size="70" />
			<div v-else class="attribute-list">
				<div v-for="row in object.attributes" :key="row.id" class="attribute-card" @click="openEditor(row.id)">
					<div class="attribute-card-head">
						<span class="attribute-name">{{ row.name || '未命名属性' }}</span>
						<ElTag :type="row.required ? 'danger' : 'info'" size="small" effect="plain">{{ row.required ? '必填' : '可选' }}</ElTag>
						<span class="attribute-type">{{ typeLabel(row.dataType) }}</span>
					</div>
					<div class="attribute-meta">
						<span>{{ row.id || '未设置标识' }}</span>
						<span v-if="row.dataType === 'Enumeration'">枚举 {{ (row.allowedValues || []).length }} 项</span>
						<span v-else-if="row.dataType === 'Reference'">引用 {{ referenceSummary(row) }}</span>
						<span v-else-if="row.unit">单位 {{ row.unit }}</span>
						<span v-if="row.nullable">空值：{{ nullMeaningLabel(row.nullMeaning) }}</span>
					</div>
					<div class="attribute-actions">
						<ElButton size="small" text type="primary" @click.stop="openEditor(row.id)">编辑</ElButton>
						<ElPopconfirm title="移除后需点击保存才会提交，确认移除？" confirm-button-text="移除" cancel-button-text="取消" @confirm="removeAttribute(row.id)">
							<template #reference><ElButton size="small" text type="danger" @click.stop>移除</ElButton></template>
						</ElPopconfirm>
					</div>
				</div>
			</div>
		</template>
		<template v-else>
			<div class="editor-toolbar">
				<ElButton size="small" text :icon="ArrowLeft" @click="backToList">返回属性清单</ElButton>
				<span class="editor-title">{{ isCreating ? '新增属性' : '编辑属性' }}</span>
			</div>
			<ElAlert v-if="feedback" class="editor-feedback" :title="feedback" type="success" :closable="true" show-icon @close="feedback = ''" />
			<ElAlert v-if="validationError" class="editor-feedback" :title="validationError" type="error" :closable="false" show-icon />
			<ElForm label-position="top" size="small" class="attribute-form">
				<ElFormItem label="属性标识" required>
					<ElInput :model-value="editingAttribute.id" @update:model-value="setAttributeId" />
					<div class="field-hint">稳定标识，保存后作为引用锚点；仅允许字母、数字、下划线、点或短横线。</div>
				</ElFormItem>
				<ElFormItem label="属性名" required><ElInput v-model="editingAttribute.name" placeholder="如 serviceCode" /></ElFormItem>
				<ElFormItem label="数据类型" required>
					<ElSelect class="full-width" :model-value="editingAttribute.dataType" @update:model-value="changeType">
						<ElOption v-for="type in DATA_TYPES" :key="type" :label="typeOptionLabel(type)" :value="type" />
					</ElSelect>
					<div class="field-hint">{{ typeDescription(editingAttribute.dataType) }}</div>
				</ElFormItem>
				<ElFormItem v-if="unitLabel" :label="unitLabel">
					<ElInput v-model="editingAttribute.unit" :placeholder="unitPlaceholder" />
					<div class="field-hint">{{ unitHint }}</div>
				</ElFormItem>
				<div v-if="isEnumeration" class="config-block">
					<div class="section-bar"><strong>枚举取值</strong><ElButton size="small" text type="primary" @click="addEnumValue">新增取值</ElButton></div>
					<p class="hint">枚举类型必须提供取值集合，默认值须落在集合内。</p>
					<ElEmpty v-if="enumValues.length === 0" description="枚举类型至少需要一个取值" :image-size="54" />
					<div v-for="(value, index) in enumValues" :key="index" class="enum-row">
						<ElInput v-model="value.code" placeholder="代码" />
						<ElInput v-model="value.meaning" placeholder="业务含义" />
						<ElCheckbox v-model="value.deprecated">废弃</ElCheckbox>
						<ElInput v-if="value.deprecated" v-model="value.replacedBy" placeholder="替代代码（可选）" />
						<ElButton text type="danger" @click="removeEnumValue(index)">移除</ElButton>
					</div>
				</div>
				<div v-if="isReference" class="config-block">
					<ElFormItem label="引用目标类型" required>
						<ElSelect v-model="editingAttribute.referenceTarget" class="full-width" multiple>
							<ElOption v-for="row in referenceCandidates" :key="row.id" :label="optionLabel(row)" :value="row.id" />
						</ElSelect>
						<div class="field-hint">引用类型必须声明目标对象类型，可包含本对象（自引用）。</div>
					</ElFormItem>
					<ElFormItem label="复用业务关系">
						<ElSelect v-model="editingAttribute.relationRef" class="full-width" clearable placeholder="选择已有出向关系（可选）">
							<ElOption v-for="relation in outboundRelations" :key="relation.id" :label="relationOptionLabel(relation)" :value="relation.id" />
						</ElSelect>
						<div class="field-hint">表达已有业务关系时引用同一关系定义，目标对象需与本属性的目标类型一致。</div>
					</ElFormItem>
				</div>
				<ElFormItem label="实例取值">
					<ElCheckbox v-model="editingAttribute.required" @change="requiredChanged">必须提供</ElCheckbox>
					<ElCheckbox v-model="editingAttribute.nullable" @change="nullableChanged">允许空值</ElCheckbox>
					<div class="field-hint">“必须提供”约束实例取值；“允许空值”须说明空值代表未知、未提供或不适用。</div>
				</ElFormItem>
				<ElFormItem v-if="editingAttribute.nullable" label="空值含义" required>
					<ElSelect v-model="editingAttribute.nullMeaning" class="full-width">
						<ElOption v-for="meaning in NULL_MEANINGS" :key="meaning" :label="nullMeaningLabel(meaning)" :value="meaning" />
					</ElSelect>
				</ElFormItem>
				<ElFormItem v-if="defaultKind !== 'none'" label="默认值">
					<ElSelect v-if="defaultKind === 'boolean'" v-model="booleanDefault" class="full-width">
						<ElOption label="不声明默认值" value="" />
						<ElOption label="真（true）" value="true" />
						<ElOption label="假（false）" value="false" />
					</ElSelect>
					<ElSelect v-else-if="defaultKind === 'enum'" v-model="enumDefault" class="full-width">
						<ElOption label="不声明默认值" value="" />
						<ElOption v-for="value in enumValues" :key="value.code" :label="enumOptionLabel(value)" :value="value.code" />
					</ElSelect>
					<ElInput v-else :model-value="defaultText" :placeholder="defaultPlaceholder" @update:model-value="setDefault" />
					<div v-if="defaultError" class="error-text">{{ defaultError }}</div>
					<div v-else class="field-hint">{{ defaultHint }}</div>
				</ElFormItem>
				<ElFormItem label="属性值含义"><ElInput v-model="editingAttribute.valueSemantics" type="textarea" :rows="2" placeholder="按类型解释取值口径，可后补" /></ElFormItem>
				<ElCheckbox :model-value="object.identitySpec.attributes.includes(editingAttribute.id)" @change="setIdentity">作为业务标识属性</ElCheckbox>
			</ElForm>
			<div class="editor-footer">
				<ElPopconfirm title="移除后需点击保存才会提交，确认移除？" confirm-button-text="移除" cancel-button-text="取消" @confirm="removeAttribute(editingAttribute.id)">
					<template #reference><ElButton text type="danger">移除该属性</ElButton></template>
				</ElPopconfirm>
				<ElButton size="small" type="primary" plain @click="backToList">返回属性清单</ElButton>
			</div>
		</template>
	</div>
</template>
<script setup lang="ts">
/* eslint-disable vue/no-mutating-props */
import { ArrowLeft } from "@element-plus/icons-vue";
import { computed, ref, watch } from "vue";

import { DATA_TYPES, NULL_MEANINGS, type AllowedValue, type AttributeDef, type DataType, type GraphObject, type GraphRelation, type NullMeaning, type ObjectTypeDetail } from "../../types";

const props = defineProps<{ object: ObjectTypeDetail; objects: GraphObject[]; relations: GraphRelation[] }>();

const editingId = ref<string | null>(null);
const isCreating = ref(false);
const validationError = ref("");
const defaultError = ref("");
const feedback = ref("");

const TYPE_META: Record<DataType, { label: string; description: string }> = {
	String: { label: "文本", description: "字符串类型：自由文本或受格式约束的业务编码。" },
	Integer: { label: "整数", description: "不含小数的数值，可声明计量单位与取值区间。" },
	Decimal: { label: "小数", description: "带精度的数值，可声明计量单位与取值区间。" },
	Boolean: { label: "布尔", description: "仅取真或假，不参与枚举与引用配置。" },
	DateTime: { label: "日期时间", description: "时间点，需声明时区与精度，默认值按 ISO 8601 文本记录。" },
	Reference: { label: "引用", description: "取值指向其他对象类型的实例，必须声明目标类型；可复用已有业务关系。" },
	Enumeration: { label: "枚举", description: "取值限定在受控枚举集合内，必须提供枚举取值，默认值须落在集合内。" }
};
const NULL_LABELS: Record<NullMeaning, string> = { UNKNOWN: "未知", NOT_PROVIDED: "未提供", NOT_APPLICABLE: "不适用" };

const editingAttribute = computed(() => props.object.attributes.find(row => row.id === editingId.value));
const outboundRelations = computed(() => props.relations.filter(row => row.sourceType === props.object.id));
const referenceCandidates = computed(() => props.objects);
const enumValues = computed<AllowedValue[]>(() => editingAttribute.value?.allowedValues ?? []);
const isEnumeration = computed(() => editingAttribute.value?.dataType === "Enumeration");
const isReference = computed(() => editingAttribute.value?.dataType === "Reference");
const unitLabel = computed(() => {
	switch (editingAttribute.value?.dataType) {
		case "Integer":
		case "Decimal":
			return "单位";
		case "DateTime":
			return "时间语义（时区 / 精度）";
		default:
			return "";
	}
});
const unitPlaceholder = computed(() => (editingAttribute.value?.dataType === "DateTime" ? "如 UTC / 秒精度" : "如 ms、秒、台"));
const unitHint = computed(() => (editingAttribute.value?.dataType === "DateTime" ? "日期时间须声明时区与精度，样例按 UTC、秒精度。" : "有量纲数值须声明单位与换算口径；无量纲可留空。"));
const defaultKind = computed<"none" | "text" | "number" | "boolean" | "enum">(() => {
	switch (editingAttribute.value?.dataType) {
		case undefined:
		case "Reference":
			return "none";
		case "Boolean":
			return "boolean";
		case "Enumeration":
			return "enum";
		case "Integer":
		case "Decimal":
			return "number";
		default:
			return "text";
	}
});
const defaultText = computed(() => {
	const value = editingAttribute.value?.defaultValue;
	if (value === undefined) return "";
	return typeof value === "string" ? value : JSON.stringify(value);
});
const defaultPlaceholder = computed(() => {
	switch (editingAttribute.value?.dataType) {
		case "Integer":
		case "Decimal":
			return "数字，如 1 / 1.5；留空不声明默认值";
		case "DateTime":
			return "如 2026-10-11T00:00:00Z；留空不声明默认值";
		default:
			return "文本；留空不声明默认值";
	}
});
const defaultHint = computed(() => {
	switch (editingAttribute.value?.dataType) {
		case "Boolean":
			return "布尔属性默认值只能取真或假。";
		case "Enumeration":
			return "默认值须落在上方枚举取值集合内。";
		case "DateTime":
			return "默认值须与时间语义一致，样例按 UTC 记录。";
		case "Integer":
		case "Decimal":
			return "数值默认值须与类型、单位相容。";
		default:
			return "未配置默认值表示定义不声明缺省事实，与显式空值不同。";
	}
});
const booleanDefault = computed({
	get: () => (editingAttribute.value?.defaultValue === true ? "true" : editingAttribute.value?.defaultValue === false ? "false" : ""),
	set: (value: string) => {
		const row = editingAttribute.value;
		if (!row) return;
		defaultError.value = "";
		if (!value) {
			delete row.defaultValue;
			return;
		}
		row.defaultValue = value === "true";
	}
});
const enumDefault = computed({
	get: () => (typeof editingAttribute.value?.defaultValue === "string" ? editingAttribute.value.defaultValue : ""),
	set: (value: string) => {
		const row = editingAttribute.value;
		if (!row) return;
		defaultError.value = "";
		if (!value) {
			delete row.defaultValue;
			return;
		}
		row.defaultValue = value;
	}
});

watch(
	() => props.object.id,
	() => {
		editingId.value = null;
		isCreating.value = false;
		validationError.value = "";
		defaultError.value = "";
		feedback.value = "";
	}
);
watch(editingAttribute, () => {
	validationError.value = "";
	defaultError.value = "";
});

function typeLabel(type: DataType) {
	return TYPE_META[type]?.label ?? type;
}
function typeOptionLabel(type: DataType) {
	return typeLabel(type) + "（" + type + "）";
}
function typeDescription(type: DataType | undefined) {
	return type ? TYPE_META[type]?.description ?? "" : "";
}
function nullMeaningLabel(meaning: NullMeaning | null | undefined) {
	if (!meaning) return "未声明";
	return NULL_LABELS[meaning] ?? meaning;
}
function enumOptionLabel(value: AllowedValue) {
	return value.meaning ? value.code + "（" + value.meaning + "）" : value.code;
}
function optionLabel(row: GraphObject) {
	return row.name + "（" + row.id + "）";
}
function relationOptionLabel(relation: GraphRelation) {
	return relation.name + "（" + relation.id + "）";
}
function referenceSummary(row: AttributeDef) {
	const targets = row.referenceTarget ?? [];
	if (!targets.length) return "未声明目标";
	return targets.map(id => props.objects.find(item => item.id === id)?.name ?? id).join("、");
}
function ensureTypeConfig(row: AttributeDef) {
	if (row.dataType === "Enumeration") row.allowedValues ??= [];
	if (row.dataType === "Reference") row.referenceTarget ??= [];
}
function defaultValueError(row: AttributeDef): string | null {
	const value = row.defaultValue;
	if (value === undefined) return null;
	switch (row.dataType) {
		case "Reference":
			return "引用类型不支持声明默认值";
		case "Integer":
			return Number.isInteger(value) ? null : "默认值须为整数";
		case "Decimal":
			return typeof value === "number" && Number.isFinite(value) ? null : "默认值须为数值";
		case "Boolean":
			return typeof value === "boolean" ? null : "默认值须为 true 或 false";
		case "Enumeration":
			return typeof value === "string" ? null : "默认值须为枚举代码";
		default:
			return typeof value === "string" ? null : "默认值须为文本";
	}
}
function openEditor(id: string) {
	const row = props.object.attributes.find(item => item.id === id);
	if (!row) return;
	ensureTypeConfig(row);
	editingId.value = id;
	isCreating.value = false;
	validationError.value = "";
	defaultError.value = "";
}
function backToList() {
	editingId.value = null;
	isCreating.value = false;
	validationError.value = "";
	defaultError.value = "";
}
function addAttribute() {
	const id = "ATTR-" + crypto.randomUUID().slice(0, 8).toUpperCase();
	props.object.attributes.push({ id, name: "", dataType: "String", required: false, nullable: false, nullMeaning: null, cardinality: { min: 0, max: 1 }, termRefs: [] });
	editingId.value = id;
	isCreating.value = true;
	validationError.value = "";
	defaultError.value = "";
	feedback.value = "属性 " + id + " 已添加，填写完成后保存生效";
}
function setAttributeId(value: string) {
	const row = editingAttribute.value;
	if (!row) return;
	row.id = value;
	editingId.value = value;
}
function changeType(next: DataType) {
	const row = editingAttribute.value;
	if (!row || row.dataType === next) return;
	const previous = row.dataType;
	row.dataType = next;
	delete row.defaultValue;
	if (next === "Enumeration") row.allowedValues ??= [];
	else delete row.allowedValues;
	if (next === "Reference") row.referenceTarget ??= [];
	else {
		delete row.referenceTarget;
		delete row.relationRef;
	}
	if (next !== "Integer" && next !== "Decimal" && next !== "DateTime") delete row.unit;
	defaultError.value = "";
	validationError.value = "";
	feedback.value = "类型已由" + typeLabel(previous) + "调整为" + typeLabel(next) + "，不相关配置已清空";
}
function requiredChanged() {
	const row = editingAttribute.value;
	if (!row) return;
	// 保留接口必需的基数字段，由“必须提供”同步下限，不再单独配置。
	row.cardinality.min = row.required ? Math.max(1, row.cardinality.min) : 0;
	if (typeof row.cardinality.max === "number" && row.cardinality.max < row.cardinality.min) row.cardinality.max = row.cardinality.min;
}
function nullableChanged() {
	const row = editingAttribute.value;
	if (!row) return;
	if (row.nullable) row.nullMeaning ??= "UNKNOWN";
	else row.nullMeaning = null;
	validationError.value = "";
}
function addEnumValue() {
	const row = editingAttribute.value;
	if (!row) return;
	row.allowedValues = row.allowedValues ?? [];
	row.allowedValues.push({ code: "", meaning: "", deprecated: false, replacedBy: null });
}
function removeEnumValue(index: number) {
	const row = editingAttribute.value;
	if (!row?.allowedValues) return;
	row.allowedValues.splice(index, 1);
}
function setDefault(value: string) {
	const row = editingAttribute.value;
	if (!row) return;
	defaultError.value = "";
	if (!value) {
		delete row.defaultValue;
		return;
	}
	if (defaultKind.value === "text") {
		row.defaultValue = value;
		return;
	}
	try {
		const parsed = JSON.parse(value) as unknown;
		if (defaultKind.value === "number" && typeof parsed !== "number") throw new Error("not a number");
		row.defaultValue = parsed;
	}
	catch {
		defaultError.value = "默认值不是合法数值";
	}
}
function setIdentity(checked: unknown) {
	const row = editingAttribute.value;
	if (!row) return;
	const ids = props.object.identitySpec.attributes;
	props.object.identitySpec.attributes = checked ? [...new Set([...ids, row.id])] : ids.filter(id => id !== row.id);
	props.object.identitySpec.mode = props.object.identitySpec.attributes.length > 1 ? "COMPOSITE" : "SINGLE";
}
function removeAttribute(id: string) {
	if (!props.object.attributes.some(item => item.id === id)) return;
	props.object.attributes = props.object.attributes.filter(item => item.id !== id);
	props.object.identitySpec.attributes = props.object.identitySpec.attributes.filter(value => value !== id);
	if (editingId.value === id) {
		editingId.value = null;
		isCreating.value = false;
	}
	defaultError.value = "";
	feedback.value = "属性 " + id + " 已移除，保存后生效";
}
function validate(): string | null {
	validationError.value = "";
	const fail = (row: AttributeDef, message: string) => {
		ensureTypeConfig(row);
		editingId.value = row.id;
		isCreating.value = false;
		return (validationError.value = message);
	};
	const seen = new Set<string>();
	for (const row of props.object.attributes) {
		if (!row.id.trim()) return fail(row, "属性标识不能为空");
		if (seen.has(row.id)) return fail(row, "属性标识重复：" + row.id);
		seen.add(row.id);
		if (!row.name.trim()) return fail(row, "属性 " + row.id + " 的属性名不能为空");
		if (row.nullable && !row.nullMeaning) return fail(row, "属性 " + row.name + " 允许空值时必须声明空值含义");
		const valueError = defaultValueError(row);
		if (valueError) return fail(row, "属性 " + row.name + "：" + valueError);
		if (row.dataType === "Enumeration") {
			const values = (row.allowedValues ?? []).filter(value => value.code.trim());
			if (!values.length) return fail(row, "枚举属性 " + row.name + " 至少需要一个枚举取值");
			if (values.length !== new Set(values.map(value => value.code.trim())).size) return fail(row, "属性 " + row.name + " 的枚举代码重复");
			if (row.defaultValue !== undefined && !values.some(value => value.code.trim() === row.defaultValue)) return fail(row, "属性 " + row.name + " 的默认值不在枚举集合内");
		}
		if (row.dataType === "Reference") {
			const targets = row.referenceTarget ?? [];
			if (!targets.length) return fail(row, "引用属性 " + row.name + " 必须声明目标对象类型");
			const missing = targets.find(target => !props.objects.some(object => object.id === target));
			if (missing) return fail(row, "引用属性 " + row.name + " 的目标对象不存在：" + missing);
			if (row.relationRef && !outboundRelations.value.some(relation => relation.id === row.relationRef)) return fail(row, "属性 " + row.name + " 复用的业务关系不是本对象的出向关系");
		}
	}
	return null;
}
defineExpose({ validate });
</script>
<style scoped>
.attributes-editor { padding: 4px 0; }
.section-bar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.hint { margin: 0 0 12px; color: var(--ctff-text-color-tertiary); font-size: 12px; line-height: 1.6; }
.editor-feedback { margin-bottom: 14px; }
.attribute-list { display: flex; flex-direction: column; gap: 10px; }
.attribute-card { padding: 12px; cursor: pointer; border: 1px solid var(--ctff-border-color-grid); border-radius: 8px; background: var(--ctff-brand-color-background); transition: border-color 0.15s ease, box-shadow 0.15s ease; }
.attribute-card:hover { border-color: var(--ctff-brand-color); box-shadow: 0 2px 8px rgb(56 166 242 / 12%); }
.attribute-card-head { display: flex; align-items: center; gap: 8px; }
.attribute-name { font-weight: 600; color: var(--ctff-text-color-primary); }
.attribute-type { font-size: 12px; color: var(--ctff-text-color-secondary); }
.attribute-meta { display: flex; flex-wrap: wrap; gap: 4px 10px; margin-top: 6px; font-size: 12px; color: var(--ctff-text-color-tertiary); }
.attribute-actions { display: flex; justify-content: flex-end; gap: 4px; margin-top: 6px; }
.editor-toolbar { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.editor-title { font-weight: 600; color: var(--ctff-text-color-primary); }
.attribute-form { padding-bottom: 4px; }
.config-block { padding: 12px; margin-bottom: 16px; border: 1px solid var(--ctff-border-color-grid); border-radius: 6px; background: var(--ctff-brand-color-background); }
.config-block .section-bar { margin-bottom: 8px; }
.config-block :deep(.el-form-item:last-child) { margin-bottom: 0; }
.full-width { width: 100%; }
.enum-row { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; margin-bottom: 8px; }
.enum-row .el-input { width: 130px; }
.field-hint { margin-top: 6px; color: var(--ctff-text-color-secondary); font-size: 12px; line-height: 1.6; }
.error-text { margin-top: 6px; color: var(--ctff-error-color); font-size: 12px; }
.editor-footer { display: flex; align-items: center; justify-content: space-between; margin-top: 10px; }
</style>
