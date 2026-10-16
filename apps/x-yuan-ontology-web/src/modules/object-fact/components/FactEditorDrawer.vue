<template>
	<ElDrawer
		:model-value="modelValue"
		:append-to="overlayTarget"
		:title="creating ? '新建对象实例' : (detail?.displayName ?? '实例详情')"
		size="520px"
		class="fact-editor-drawer"
		:before-close="close"
		@closed="reset"
	>
		<template #header>
			<div>
				<div class="fact-drawer-heading">
					<i v-if="typeId" :style="{ background: headerColor }" aria-hidden="true" />
					<strong>{{ creating ? "新建对象实例" : (detail?.displayName ?? "实例详情") }}</strong>
					<ElTag v-if="editing" size="small" type="warning">{{ creating ? "新建" : "编辑模式" }}</ElTag>
					<ElTag v-if="dirty" size="small" type="warning" effect="plain">有未保存修改</ElTag>
				</div>
				<div v-if="detail && !creating" class="fact-drawer-identity">
					{{ detail.businessIdentityText }} · {{ detail.instanceId }}
				</div>
				<div v-if="detail && !creating" class="fact-drawer-meta">
					<ElTag size="small" effect="plain">{{ detail.typeName }}</ElTag
					><ElTag size="small" type="info" effect="plain">{{ detail.domainName || "模型定义已失效" }}</ElTag>
				</div>
			</div>
		</template>
		<div v-loading="loading">
			<ElAlert v-if="error" :title="error.message" type="error" :closable="false" show-icon class="fact-drawer-alert">
				<div v-for="(item, index) in error.details ?? []" :key="index">{{ item.fieldPath }}：{{ item.message }}</div>
				<ElButton v-if="error.code === 'OF_REVISION_CONFLICT'" size="small" text type="primary" @click="emit('reload')"
					>重新加载实例</ElButton
				>
			</ElAlert>
			<ElAlert
				v-for="(mark, index) in detail?.marks ?? []"
				:key="index"
				:type="mark.markKind === 'invalidRef' ? 'error' : 'warning'"
				:title="`${mark.markKind === 'invalidRef' ? '引用失效' : '待复核'} · ${mark.reason}`"
				:closable="false"
				class="fact-drawer-alert"
			/>
			<ElTabs v-model="activeTab">
				<ElTabPane label="概要" name="overview">
					<template v-if="creating">
						<ElForm label-position="top">
							<ElFormItem label="对象类型" required
								><ElSelect
									v-model="typeId"
									:append-to="overlayTarget"
									filterable
									placeholder="选择已定义的对象类型"
									@change="changeType"
									><ElOption
										v-for="objectType in availableTypes"
										:key="objectType.id"
										:value="objectType.id"
										:label="`${objectType.name} · ${objectType.id}`" /></ElSelect
							></ElFormItem>
						</ElForm>
						<p class="fact-help">按类型填写业务标识与属性值。创建后可在属性、关系页签继续编辑，或从画布连接到其他实例。</p>
					</template>
					<ElDescriptions v-else-if="detail" :column="1" border>
						<ElDescriptionsItem label="业务标识">{{ detail.businessIdentityText }}</ElDescriptionsItem>
						<ElDescriptionsItem label="稳定标识">{{ detail.instanceId }}</ElDescriptionsItem>
						<ElDescriptionsItem label="对象类型">{{ detail.typeName }} · {{ detail.objectTypeRef }}</ElDescriptionsItem>
						<ElDescriptionsItem label="所属领域">{{ detail.domainName || "模型定义已失效" }}</ElDescriptionsItem>
						<ElDescriptionsItem label="来源">{{
							detail.sourceRef === "MANUAL" ? "手工创建" : detail.sourceRef
						}}</ElDescriptionsItem>
						<ElDescriptionsItem label="导入批次">{{ detail.batchId ?? "—" }}</ElDescriptionsItem>
					</ElDescriptions>
					<template v-if="creating">
						<FactPropertyField
							v-for="attribute in scalarAttributes"
							:key="attribute.id"
							v-model="fields[attribute.name]"
							:attribute="attribute"
							:identity="isIdentity(attribute.id, attribute.name)"
							:editing="true"
							:creating="true"
							:error="fieldError(attribute.name)"
						/>
					</template>
					<template v-else-if="detail">
						<div class="overview-actions">
							<ElButton plain size="small" @click="emit('focus', detail.instanceId)">聚焦一跳邻域</ElButton
							><ElButton text size="small" @click="activeTab = 'properties'">查看全部属性</ElButton>
						</div>
						<p v-if="type" class="fact-help">{{ type.definition }}</p>
					</template>
				</ElTabPane>
				<ElTabPane v-if="!creating" label="属性" name="properties">
					<FactPropertyField
						v-for="attribute in scalarAttributes"
						:key="attribute.id"
						v-model="fields[attribute.name]"
						:attribute="attribute"
						:identity="isIdentity(attribute.id, attribute.name)"
						:editing="editing"
						:creating="false"
						:error="fieldError(attribute.name)"
					/>
					<div v-for="attribute in referenceAttributes" :key="attribute.id" class="derived-property">
						<div>{{ attribute.name }} <ElTag size="small" type="info" effect="plain">由关系派生</ElTag></div>
						<ElButton
							v-for="instance in detail?.referenceValues[attribute.name] ?? []"
							:key="instance.instanceId"
							size="small"
							text
							type="primary"
							@click="emit('select', instance.instanceId)"
							>{{ instance.businessIdentityText }}</ElButton
						>
						<span v-if="!detail?.referenceValues[attribute.name]?.length" class="fact-help">{{
							nullMeaningLabels[attribute.nullMeaning ?? ""] ?? "未关联实例"
						}}</span>
					</div>
					<div v-for="name in unknownFields" :key="name" class="derived-property">
						<ElTag size="small" type="warning">模型已移除属性</ElTag> {{ name }}：{{ displayValue(detail?.propertyValues[name]) }}
						<ElButton
							v-if="editing"
							text
							size="small"
							:type="removedProperties.has(name) ? 'primary' : 'danger'"
							@click="togglePropertyRemoval(name)"
							>{{ removedProperties.has(name) ? "恢复" : "移除失效属性" }}</ElButton
						>
					</div>
					<ElEmpty
						v-if="!scalarAttributes.length && !referenceAttributes.length && !unknownFields.length"
						description="该类型尚未定义属性"
					/>
				</ElTabPane>
				<ElTabPane v-if="!creating" label="关系" name="relations">
					<template v-for="direction in ['outbound', 'inbound'] as const" :key="direction">
						<div class="relation-section-heading">
							{{ direction === "outbound" ? "出向关系" : "入向关系" }} <span>{{ detail?.[direction].length ?? 0 }}</span>
						</div>
						<div
							v-for="relation in detail?.[direction] ?? []"
							:key="`${direction}:${relation.relationId}`"
							class="fact-relation-row"
							:class="{ 'is-removed': removed.has(relation.relationId) }"
						>
							<div>
								<strong>{{ relation.relationName }}</strong
								><span class="fact-help">{{ direction === "outbound" ? "→" : "←" }}</span
								><ElButton text size="small" @click="emit('select', relation.other.instanceId)">{{
									relation.other.businessIdentityText
								}}</ElButton>
							</div>
							<ElButton
								v-if="editing"
								size="small"
								text
								:type="removed.has(relation.relationId) ? 'primary' : 'danger'"
								@click="toggleRemove(relation.relationId)"
								>{{ removed.has(relation.relationId) ? "恢复" : "移除" }}</ElButton
							>
						</div>
						<div v-if="!detail?.[direction].length" class="relation-empty">
							暂无{{ direction === "outbound" ? "出向" : "入向" }}关系
						</div>
					</template>
					<div v-for="(addition, index) in additions" :key="index" class="fact-relation-row is-added">
						<span
							><ElTag size="small" type="success">新增</ElTag> {{ relationName(addition.relationTypeRef) }} →
							{{ targetName(addition.targetInstanceId) }}</span
						><ElButton size="small" text type="danger" @click="additions.splice(index, 1)">撤销</ElButton>
					</div>
					<div v-if="editing" class="relation-add-form">
						<div class="relation-section-heading">新增出向关系</div>
						<ElSelect
							v-model="newRelationType"
							:append-to="overlayTarget"
							placeholder="选择关系类型"
							filterable
							@change="loadTargets('')"
							><ElOption v-for="relation in outgoingTypes" :key="relation.id" :value="relation.id" :label="relation.name"
						/></ElSelect>
						<ElSelect
							v-model="newTarget"
							:append-to="overlayTarget"
							remote
							filterable
							:remote-method="loadTargets"
							:loading="targetsLoading"
							:disabled="!newRelationType"
							placeholder="按业务标识或名称搜索目标实例"
							@visible-change="targetsVisible"
							><ElOption
								v-for="target in targetOptions"
								:key="target.instanceId"
								:value="target.instanceId"
								:label="`${target.businessIdentityText} · ${target.displayName}`"
						/></ElSelect>
						<ElButton size="small" plain :disabled="!newRelationType || !newTarget" @click="addRelation">加入修改集</ElButton>
						<p class="fact-help">保存时按“现有关联 − 移除 + 新增”整体校验，失败时保留当前修改。</p>
					</div>
				</ElTabPane>
				<ElTabPane v-if="!creating" label="来源与引用" name="source">
					<ElDescriptions v-if="detail" :column="1" border
						><ElDescriptionsItem label="来源标识">{{ detail.sourceRef }}</ElDescriptionsItem
						><ElDescriptionsItem label="文件内标识">{{ detail.instanceKey ?? "—" }}</ElDescriptionsItem
						><ElDescriptionsItem label="导入批次">{{ detail.batchId ?? "—" }}</ElDescriptionsItem
						><ElDescriptionsItem label="创建时间">{{ detail.createdAt }}</ElDescriptionsItem
						><ElDescriptionsItem label="最近修改">{{ detail.updatedAt ?? "—" }}</ElDescriptionsItem></ElDescriptions
					>
					<p class="fact-help">双向引用可在关系页签查看并跳转。操作记录可从工作台右上角查阅。</p>
				</ElTabPane>
			</ElTabs>
		</div>
		<template #footer>
			<div class="fact-drawer-footer">
				<ElButton v-if="!creating && writable" type="danger" text :disabled="saving" @click="emit('delete')">删除实例</ElButton>
				<span class="footer-spacer" />
				<template v-if="editing"
					><ElButton :disabled="saving" @click="cancelEdit">取消</ElButton
					><ElButton type="primary" :loading="saving" :disabled="!type || (!creating && !dirty)" @click="save">{{
						creating ? "创建实例" : "保存"
					}}</ElButton></template
				>
				<template v-else
					><ElButton @click="close()">关闭</ElButton
					><ElButton v-if="writable" type="primary" :disabled="!type" @click="editing = true">编辑</ElButton></template
				>
			</div>
		</template>
	</ElDrawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { relationTargetIds } from "@/modules/object-model/types";
import { factApi } from "../api";
import { changedValues, displayValue, initialFields, nullMeaningLabels, propertyValues } from "../form";
import { useFactOverlay } from "../fullscreen";
import { factTypeColor } from "../presentation";
import type { FactCatalog, FactCreate, FactDetail, FactEdit, FactError, FactSummary, RelationOp } from "../types";
import FactPropertyField from "./FactPropertyField.vue";

const overlayTarget = useFactOverlay();

const props = defineProps<{
	modelValue: boolean;
	creating: boolean;
	detail: FactDetail | null;
	catalog: FactCatalog;
	writable: boolean;
	saving: boolean;
	loading: boolean;
	error: FactError | null;
}>();
const emit = defineEmits<{
	(event: "update:modelValue", value: boolean): void;
	(event: "save", body: FactCreate | FactEdit): void;
	(event: "delete"): void;
	(event: "reload"): void;
	(event: "select", id: string): void;
	(event: "focus", id: string): void;
}>();
const activeTab = ref("overview"),
	typeId = ref("");
const editing = ref(false);
const fields = ref<Record<string, unknown>>({});
const baseline = ref("");
const removed = ref(new Set<string>());
const removedProperties = ref(new Set<string>());
const additions = ref<Extract<RelationOp, { op: "add" }>[]>([]);
const newRelationType = ref(""),
	newTarget = ref("");
const targetOptions = ref<FactSummary[]>([]);
const targetsLoading = ref(false);
let targetRequest = 0;
const type = computed(() => props.catalog.types.find(value => value.id === typeId.value));
const headerColor = computed(() => factTypeColor(typeId.value));
const availableTypes = computed(() => props.catalog.types.filter(value => value.status !== "deprecated"));
const scalarAttributes = computed(() => type.value?.attributes.filter(value => value.dataType !== "Reference") ?? []);
const referenceAttributes = computed(() => type.value?.attributes.filter(value => value.dataType === "Reference") ?? []);
const outgoingTypes = computed(() =>
	props.catalog.relations.filter(value => value.sourceType === typeId.value && value.status !== "deprecated")
);
const unknownFields = computed(() =>
	Object.keys(props.detail?.propertyValues ?? {}).filter(name => !type.value?.attributes.some(value => value.name === name))
);
const dirty = computed(
	() =>
		JSON.stringify(fields.value) !== baseline.value ||
		removed.value.size > 0 ||
		additions.value.length > 0 ||
		removedProperties.value.size > 0
);

function isIdentity(id: string, name: string) {
	return type.value?.identitySpec.attributes.some(reference => reference === id || reference === name);
}
function fieldError(name: string) {
	return props.error?.details
		?.filter(item => item.fieldPath === `propertyValues.${name}`)
		.map(item => item.message)
		.join("；");
}
function reset() {
	typeId.value = props.creating ? (availableTypes.value[0]?.id ?? "") : (props.detail?.objectTypeRef ?? "");
	fields.value = initialFields(type.value, props.creating ? undefined : props.detail?.propertyValues);
	baseline.value = JSON.stringify(fields.value);
	removed.value = new Set();
	removedProperties.value = new Set();
	additions.value = [];
	editing.value = props.creating;
	newRelationType.value = "";
	newTarget.value = "";
	targetOptions.value = [];
}
watch(
	() => [props.modelValue, props.detail, props.creating],
	() => {
		if (props.modelValue) {
			reset();
			activeTab.value = "overview";
		}
	}
);
function changeType() {
	fields.value = initialFields(type.value);
	baseline.value = "";
}
function toggleRemove(id: string) {
	const next = new Set(removed.value);
	if (next.has(id)) next.delete(id);
	else next.add(id);
	removed.value = next;
}
function togglePropertyRemoval(name: string) {
	const next = new Set(removedProperties.value);
	if (next.has(name)) next.delete(name);
	else next.add(name);
	removedProperties.value = next;
}
function relationName(id: string) {
	return props.catalog.relations.find(value => value.id === id)?.name ?? id;
}
function targetName(id: string) {
	return targetOptions.value.find(value => value.instanceId === id)?.businessIdentityText ?? id;
}
function targetsVisible(visible: boolean) {
	if (visible) void loadTargets("");
}
async function loadTargets(query: string) {
	const relation = outgoingTypes.value.find(value => value.id === newRelationType.value);
	if (!relation) return;
	const token = ++targetRequest;
	targetsLoading.value = true;
	try {
		const result = await factApi.list({ type: relationTargetIds(relation).join(","), q: query, pageSize: 100 });
		if (token === targetRequest) targetOptions.value = result.items;
	} catch (error) {
		ElMessage.error({ message: (error as FactError).message, appendTo: overlayTarget.value });
	} finally {
		if (token === targetRequest) targetsLoading.value = false;
	}
}
function addRelation() {
	if (!props.detail || !newTarget.value || !newRelationType.value) return;
	additions.value.push({
		op: "add",
		relationTypeRef: newRelationType.value,
		sourceInstanceId: props.detail.instanceId,
		targetInstanceId: newTarget.value
	});
	newTarget.value = "";
}
async function canLeave() {
	if (props.saving) return false;
	if (!dirty.value) return true;
	try {
		await ElMessageBox.confirm("当前修改尚未保存，是否放弃修改？", "未保存修改", {
			appendTo: overlayTarget.value,
			confirmButtonText: "放弃修改",
			cancelButtonText: "继续编辑",
			type: "warning"
		});
		return true;
	} catch {
		return false;
	}
}
async function close(done?: () => void) {
	if (await canLeave()) {
		done?.();
		emit("update:modelValue", false);
	}
}
async function cancelEdit() {
	if (!(await canLeave())) return;
	if (props.creating) emit("update:modelValue", false);
	else reset();
}
function save() {
	if (!props.writable || props.saving) return;
	try {
		const values = propertyValues(type.value, fields.value);
		if (props.creating) emit("save", { objectTypeRef: typeId.value, propertyValues: values });
		else {
			const previous = { ...props.detail?.propertyValues };
			for (const name of unknownFields.value) delete previous[name];
			emit("save", {
				propertyValues: changedValues(previous, values, type.value),
				unsetProperties: [...removedProperties.value],
				relationOps: [...[...removed.value].map(relationId => ({ op: "remove" as const, relationId })), ...additions.value]
			});
		}
	} catch (error) {
		ElMessage.error({ message: (error as Error).message, appendTo: overlayTarget.value });
	}
}
function showRelations() {
	activeTab.value = "relations";
}
defineExpose({ canLeave, showRelations });
</script>

<style scoped>
.fact-drawer-heading,
.fact-drawer-footer {
	display: flex;
	gap: 8px;
	align-items: center;
}

.fact-drawer-heading strong {
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.fact-drawer-heading > i {
	flex-shrink: 0;
	width: 10px;
	height: 10px;
	border-radius: 50%;
}

.fact-drawer-identity {
	margin-top: 5px;
	font-family: Consolas, Menlo, monospace;
	font-size: 11px;
	color: var(--el-text-color-secondary);
	overflow-wrap: anywhere;
}

.fact-drawer-meta {
	display: flex;
	flex-wrap: wrap;
	gap: 6px;
	margin-top: 8px;
}

.footer-spacer {
	flex: 1;
}

.fact-drawer-alert {
	margin-bottom: 10px;
}

.fact-help,
.relation-empty {
	font-size: 12px;
	line-height: 1.7;
	color: var(--el-text-color-secondary);
}

p.fact-help {
	margin: 12px 0;
}

.overview-actions {
	display: flex;
	gap: 8px;
	margin-top: 16px;
}

.relation-section-heading {
	display: flex;
	gap: 8px;
	margin: 18px 0 8px;
	font-size: 13px;
	font-weight: 600;
}

.relation-section-heading span {
	color: var(--el-text-color-placeholder);
}

.fact-relation-row {
	display: flex;
	gap: 8px;
	align-items: center;
	justify-content: space-between;
	padding: 8px 0;
	font-size: 12px;
	border-bottom: 1px solid var(--el-border-color-lighter);
}

.fact-relation-row > div {
	display: flex;
	gap: 8px;
	align-items: center;
	min-width: 0;
}

.fact-relation-row.is-removed > div {
	text-decoration: line-through;
	opacity: 0.45;
}

.fact-relation-row.is-added {
	color: var(--el-color-success);
}

.relation-add-form {
	display: flex;
	flex-direction: column;
	gap: 10px;
	padding-top: 8px;
	margin-top: 14px;
	border-top: 1px solid var(--el-border-color);
}

.derived-property {
	padding: 12px 0;
	font-size: 13px;
	border-bottom: 1px solid var(--el-border-color-lighter);
}

.derived-property > div {
	margin-bottom: 5px;
}

.el-select {
	width: 100%;
}
</style>
