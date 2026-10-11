<template>
	<div class="object-model-page">
		<div class="model-body">
			<section class="canvas-panel">
				<div class="canvas-toolbar">
					<span class="stat-chip"><i class="status-dot" />{{ store.visibleObjects.length }} 对象 · {{ store.visibleRelations.length }} 关系</span>
					<span class="domain-legend"><i class="domain-legend-mark" />圆点颜色表示所属领域</span>
					<ElSelect v-model="store.domainFilter" clearable placeholder="全部领域" size="small" class="domain-select">
						<ElOption v-for="domain in store.domains" :key="domain.domainId" :label="domain.name" :value="domain.domainId">
							<span class="domain-option"><i class="domain-dot" :style="{ background: store.domainColorMap.get(domain.domainId) }" />{{ domain.name }}（{{ domain.objectCount ?? 0 }}）</span>
						</ElOption>
					</ElSelect>
					<div class="search-cluster">
						<ElInput v-model="store.searchText" clearable size="small" class="model-search" placeholder="搜索对象 / 标识 / 属性名…" @keyup.enter="focusSearchResult(searchCursor)">
							<template #prefix><ElIcon><Search /></ElIcon></template>
						</ElInput>
						<template v-if="store.searchText.trim()">
							<ElPopover placement="bottom-start" trigger="click" width="320">
								<template #reference>
									<button type="button" class="search-results-trigger" :class="{ 'is-empty': !searchResults.length }">
										{{ searchResults.length ? `${searchCursor + 1}/${searchResults.length}` : "无结果" }}
									</button>
								</template>
								<div class="search-results-panel">
									<div class="search-results-header">
										<span>搜索结果</span>
										<span class="search-results-count">{{ searchResults.length }} 项</span>
									</div>
									<button v-for="(result, index) in searchResults" :key="result.id" type="button" class="search-result-item" :class="{ 'is-current': index === searchCursor }" @click="focusSearchResult(index)">
										<span class="search-result-name">{{ result.name }}</span>
										<span class="search-result-code">{{ result.id }}</span>
									</button>
									<div v-if="!searchResults.length" class="search-empty">未找到匹配对象，请尝试对象名称、编码或标识属性。</div>
								</div>
							</ElPopover>
							<ElButton size="small" text :icon="ArrowUp" :disabled="!searchResults.length" aria-label="上一个搜索结果" @click="focusSearchResult(searchCursor - 1)" />
							<ElButton size="small" text :icon="ArrowDown" :disabled="!searchResults.length" aria-label="下一个搜索结果" @click="focusSearchResult(searchCursor + 1)" />
						</template>
					</div>
					<span class="toolbar-spacer" />
					<ElDropdown trigger="click" @command="handleLayoutCommand">
						<ElButton size="small" text :icon="Sort">自动布局<ElIcon class="el-icon--right"><ArrowDown /></ElIcon></ElButton>
						<template #dropdown>
							<ElDropdownMenu>
								<ElDropdownItem command="horizontal">横向层级布局{{ selectedObjectIds.length > 1 ? "（选中）" : "（全图）" }}</ElDropdownItem>
								<ElDropdownItem command="vertical">纵向层级布局{{ selectedObjectIds.length > 1 ? "（选中）" : "（全图）" }}</ElDropdownItem>
							</ElDropdownMenu>
						</template>
					</ElDropdown>
					<ElButton size="small" text :icon="Refresh" :loading="store.loading" @click="store.loadGraph()">刷新</ElButton>
					<ElButton size="small" type="primary" plain :icon="Plus" @click="store.startCreateObject()">新建对象</ElButton>
					<ElButton size="small" type="primary" :icon="Download" @click="exportModel">导出 JSON</ElButton>
				</div>
				<div ref="canvasHostRef" v-loading="store.loading" class="canvas-host">
					<ObjectCanvas
						ref="canvasRef"
						:objects="store.visibleObjects"
						:relations="store.visibleRelations"
						:domain-color-map="store.domainColorMap"
						:domain-name-map="store.domainNameMap"
						:highlighted-ids="store.highlightedIds"
						:selected-object-ids="selectedObjectIds"
						:selected-object-id="store.drawerVisible ? store.currentObject?.id : undefined"
						:selected-relation-id="store.drawerVisible ? store.currentRelation?.id : undefined"
						@select-object="store.openObject"
						@select-relation="store.openRelation"
						@create-relation="store.startCreateRelation"
						@nodes-moved="handleNodesMoved"
						@selection-change="handleSelectionChange"
						@zoom-change="zoomPercent = $event"
						@canvas-click="handleCanvasClick"
					/>
					<div v-if="selectedObjectIds.length > 1" class="selection-actions">
						<span class="selection-actions-label">已选 {{ selectedObjectIds.length }} 个</span>
						<ElButtonGroup>
							<ElButton size="small" text title="左对齐" @click="alignSelection('left')">左</ElButton>
							<ElButton size="small" text title="水平居中" @click="alignSelection('center')">中</ElButton>
							<ElButton size="small" text title="右对齐" @click="alignSelection('right')">右</ElButton>
							<ElButton size="small" text title="上对齐" @click="alignSelection('top')">上</ElButton>
							<ElButton size="small" text title="垂直居中" @click="alignSelection('middle')">中</ElButton>
							<ElButton size="small" text title="下对齐" @click="alignSelection('bottom')">下</ElButton>
							<ElButton size="small" text title="水平等距" :disabled="selectedObjectIds.length < 3" @click="alignSelection('distribute-x')">横向等距</ElButton>
							<ElButton size="small" text title="垂直等距" :disabled="selectedObjectIds.length < 3" @click="alignSelection('distribute-y')">纵向等距</ElButton>
						</ElButtonGroup>
					</div>
					<div class="canvas-bottom-left">
						<ElButton size="small" text :icon="RefreshLeft" :disabled="!store.layoutUndoStack.length || store.layoutBusy" title="撤销布局（Ctrl/Cmd+Z）" @click="undoLayout">撤销</ElButton>
						<ElButton size="small" text :icon="RefreshRight" :disabled="!store.layoutRedoStack.length || store.layoutBusy" title="重做布局（Ctrl/Cmd+Shift+Z）" @click="redoLayout">重做</ElButton>
					</div>
					<div class="canvas-bottom-right">
						<div class="zoom-control" role="group" aria-label="画布缩放">
							<button type="button" class="zoom-button" aria-label="缩小" @click="canvasRef?.zoomOut()"><ElIcon><ZoomOut /></ElIcon></button>
							<ElDropdown placement="top" trigger="click" popper-class="zoom-dropdown-popper" @command="selectZoomPreset">
								<div class="zoom-value-editor" role="button" tabindex="0" aria-haspopup="menu" aria-label="选择缩放比例">
									<input
										v-model="zoomInput"
										class="zoom-input"
										type="text"
										inputmode="numeric"
										aria-label="缩放比例"
										@keydown.enter.prevent="applyZoomInput"
										@blur="applyZoomInput"
									/>
									<span class="zoom-percent-sign">%</span>
								</div>
								<template #dropdown>
									<ElDropdownMenu>
										<ElDropdownItem v-for="preset in zoomPresets" :key="preset" :command="preset" :class="{ 'is-active': preset === zoomPercent }">{{ preset }}%</ElDropdownItem>
									</ElDropdownMenu>
								</template>
							</ElDropdown>
							<button type="button" class="zoom-button" aria-label="放大" @click="canvasRef?.zoomIn()"><ElIcon><ZoomIn /></ElIcon></button>
						</div>
						<ElButton size="small" text :icon="FullScreen" @click="fitCanvas">适应画布</ElButton>
						<ElButton size="small" text :icon="FullScreen" @click="toggleFullscreen">{{ isFullscreen ? "退出全屏" : "全屏" }}</ElButton>
					</div>
					<div v-if="store.loadError && !store.loading" class="canvas-error">
						<ElEmpty description="对象模型暂时无法加载">
							<ElButton type="primary" plain @click="store.loadGraph">重新加载</ElButton>
						</ElEmpty>
					</div>
				</div>
			</section>
		</div>

		<ElDrawer v-model="store.drawerVisible" :title="drawerTitle" direction="rtl" size="480px" destroy-on-close class="model-drawer" @closed="store.closeDrawer">
			<template #header>
				<div class="drawer-header">
					<ElButton v-if="navContext" size="small" text :icon="ArrowLeft" class="back-button" @click="goBack">{{ navContext }}</ElButton>
					<span class="drawer-title-text">{{ drawerTitle }}</span>
				</div>
			</template>
			<div v-if="drawerDirty || store.saveError" class="drawer-status">
				<ElTag v-if="drawerDirty" type="warning" effect="light">有未保存修改</ElTag>
				<ElAlert v-if="store.saveError" type="error" :closable="false" show-icon class="save-error-alert">
					<template #title>保存失败 · {{ store.saveError.code }}</template>
					<div class="save-error-body">
						<span>{{ store.saveError.message }}</span>
						<span v-if="store.saveError.details?.length" class="save-error-details">字段：{{ store.saveError.details.map(item => item.field).join("、") }}</span>
						<ElButton v-if="store.saveError.retryable" size="small" text type="primary" @click="saveDrawer">重试</ElButton>
					</div>
				</ElAlert>
			</div>
			<template v-if="store.drawerMode === 'object' || store.drawerMode === 'create-object'">
				<ElTabs v-model="activeTab" class="model-tabs">
					<ElTabPane label="概要" name="overview">
						<OverviewTab
							ref="overviewRef"
							v-model="objectDraft"
							:mode="store.drawerMode === 'create-object' ? 'create' : 'edit'"
							:domains="store.domains"
							:revision="store.currentObject?.revision"
							:completeness="store.currentObject?.completeness"
							@domain-created="handleDomainCreated"
						/>
					</ElTabPane>
					<ElTabPane label="属性" name="attributes" :disabled="store.drawerMode === 'create-object'">
						<AttributesEditor v-if="objectDetailDraft" ref="attributesRef" :object="objectDetailDraft" :objects="store.objects" :relations="store.relations" />
					</ElTabPane>
					<ElTabPane label="关系" name="object-relations" :disabled="store.drawerMode === 'create-object'">
						<RelationList v-if="store.currentObject" :object-id="store.currentObject.id" :relations="store.relations" @open="openRelationFromObject" />
					</ElTabPane>
					<ElTabPane label="约束" name="constraints" disabled><PlaceholderTab text="约束建模将在规则建模能力启用后接入。" /></ElTabPane>
					<ElTabPane label="事件" name="events" disabled><PlaceholderTab text="待事件建模需求（Q-OM-009）。" /></ElTabPane>
					<ElTabPane label="动作" name="actions" disabled><PlaceholderTab text="待行为建模需求。" /></ElTabPane>
				</ElTabs>
			</template>
			<RelationEditor v-else ref="relationEditorRef" :relation="relationDraft" :objects="store.objects" :creating="store.drawerMode === 'create-relation'" />
			<template #footer>
				<div class="drawer-footer">
					<ElPopconfirm v-if="store.drawerMode === 'object' && activeTab === 'overview'" title="废弃后对象将从画布隐藏，确认继续？" confirm-button-text="废弃" cancel-button-text="取消" @confirm="deprecateObject">
						<template #reference><ElButton type="danger" plain :loading="store.saving">废弃对象</ElButton></template>
					</ElPopconfirm>
					<ElPopconfirm v-if="store.drawerMode === 'relation'" title="删除关系后将从模型中移除，确认继续？" confirm-button-text="删除" cancel-button-text="取消" @confirm="store.deleteCurrentRelation()">
						<template #reference><ElButton type="danger" plain :loading="store.saving">删除关系</ElButton></template>
					</ElPopconfirm>
					<span class="footer-spacer" />
					<ElButton v-if="drawerDirty" text :disabled="store.saving" @click="resetDrafts">撤销修改</ElButton>
					<ElButton @click="store.closeDrawer">关闭</ElButton>
					<ElButton type="primary" :loading="store.saving" :disabled="!drawerDirty" @click="saveDrawer">保存</ElButton>
				</div>
			</template>
		</ElDrawer>
	</div>
</template>

<script setup lang="ts">
import { ElMessage } from "element-plus";
import {
	ArrowDown,
	ArrowLeft,
	ArrowUp,
	Download,
	FullScreen,
	Plus,
	Refresh,
	RefreshLeft,
	RefreshRight,
	Search,
	Sort,
	ZoomIn,
	ZoomOut
} from "@element-plus/icons-vue";
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";

import { objectModelApi } from "./api";
import { alignedPositions, layeredLayout, type AlignAction, type Positions } from "./canvas-layout";
import AttributesEditor from "./components/tabs/AttributesEditor.vue";
import ObjectCanvas from "./components/ObjectCanvas.vue";
import OverviewTab, { type OverviewDraft } from "./components/tabs/OverviewTab.vue";
import RelationEditor from "./components/tabs/RelationEditor.vue";
import RelationList from "./components/tabs/RelationList.vue";
import { useObjectModelStore } from "./store";
import type { DomainDef, ObjectTypeDetail, RelationDetail } from "./types";

const store = useObjectModelStore();
const canvasRef = ref<InstanceType<typeof ObjectCanvas>>();
const canvasHostRef = ref<HTMLElement>();
const overviewRef = ref<InstanceType<typeof OverviewTab>>();
const attributesRef = ref<InstanceType<typeof AttributesEditor>>();
const relationEditorRef = ref<InstanceType<typeof RelationEditor>>();
const activeTab = ref("overview");
const objectDraft = reactive<OverviewDraft>({ id: "", name: "", definition: "", domainId: "" });
const relationDraft = reactive<Partial<RelationDetail>>({});
const objectDetailDraft = ref<ObjectTypeDetail | null>(null);
const selectedObjectIds = ref<string[]>([]);
const zoomPercent = ref(100);
const zoomInput = ref("100");
const zoomPresets = [50, 75, 100, 125, 150, 200];
const isFullscreen = ref(false);
const searchCursor = ref(0);

const searchResults = computed(() => {
	const keyword = store.searchText.trim().toLowerCase();
	if (!keyword) return [];
	return store.visibleObjects.filter(object =>
		object.name.toLowerCase().includes(keyword) ||
		object.id.toLowerCase().includes(keyword) ||
		object.identityAttrNames.some(name => name.toLowerCase().includes(keyword))
	);
});

// 关系浮窗内记录"来自哪个对象"，用于返回
const relationReturnObjectId = ref<string | null>(null);

const navContext = computed(() => {
	if (store.drawerMode === "relation" && relationReturnObjectId.value) {
		const source = store.objects.find(object => object.id === relationReturnObjectId.value);
		return source ? `返回 ${source.name}` : "返回对象";
	}
	return "";
});

const drawerTitle = computed(() => {
	if (store.drawerMode.includes("relation")) {
		return store.drawerMode === "create-relation" ? "新建业务关系" : `关系配置 · ${store.currentRelation?.name ?? ""}`;
	}
	if (store.drawerMode === "create-object") {
		return "新建对象类型";
	}
	return store.currentObject?.name || "对象配置";
});

function clone<T>(value: T): T {
	return JSON.parse(JSON.stringify(value)) as T;
}

function stable(value: unknown): string {
	if (Array.isArray(value)) return `[${value.map(item => stable(item)).join(",")}]`;
	if (value && typeof value === "object") {
		return `{${Object.keys(value as Record<string, unknown>).sort().map(key => `${JSON.stringify(key)}:${stable((value as Record<string, unknown>)[key])}`).join(",")}}`;
	}
	return JSON.stringify(value);
}

const drawerDirty = computed(() => {
	if (store.drawerMode === "create-object" || store.drawerMode === "create-relation") return true;
	if (store.drawerMode === "object" && store.currentObject && objectDetailDraft.value) {
		return stable({
			overview: objectDraft,
			attributes: objectDetailDraft.value.attributes,
			identitySpec: objectDetailDraft.value.identitySpec
		}) !== stable({
			overview: {
				id: store.currentObject.id,
				name: store.currentObject.name,
				definition: store.currentObject.definition,
				domainId: store.currentObject.domainId
			},
			attributes: store.currentObject.attributes,
			identitySpec: store.currentObject.identitySpec
		});
	}
	if (store.drawerMode === "relation" && store.currentRelation) {
		return stable(relationDraft) !== stable(store.currentRelation);
	}
	return false;
});

watch(
	() => store.currentObject,
	object => {
		if (object) {
			Object.assign(objectDraft, { id: object.id, name: object.name, definition: object.definition, domainId: object.domainId });
			objectDetailDraft.value = clone(object);
		}
		else {
			Object.assign(objectDraft, { id: "", name: "", definition: "", domainId: store.domains[0]?.domainId || "" });
			objectDetailDraft.value = null;
		}
	},
	{ immediate: true }
);
watch(() => store.currentRelation, relation => {
		for (const key of Object.keys(relationDraft)) delete relationDraft[key as keyof typeof relationDraft];
		Object.assign(relationDraft, clone(relation ?? {}));
});
watch(
	() => store.drawerMode,
	mode => {
		activeTab.value = mode.includes("relation") ? "overview" : "overview";
		if (mode !== "relation") {
			relationReturnObjectId.value = null;
		}
		if (mode === "create-relation") {
			const source = store.pendingRelationSource || "";
			Object.assign(relationDraft, {
				id: undefined,
				name: "",
				definition: "",
				sourceType: source,
				targetType: store.pendingRelationTarget || "",
				sourceCardinality: { min: 0, max: 1 },
				targetCardinality: { min: 0, max: "N" },
				compositionKind: "Association",
				domainId: store.objects.find(item => item.id === source)?.domainId || "",
				crossDomainDeclared: false,
				revision: 1
			});
		}
	}
);

onMounted(() => {
	void store.loadGraph();
	window.addEventListener("keydown", handleKeydown);
	document.addEventListener("fullscreenchange", handleFullscreenChange);
});

onBeforeUnmount(() => {
	window.removeEventListener("keydown", handleKeydown);
	document.removeEventListener("fullscreenchange", handleFullscreenChange);
});

watch(() => store.searchText, () => {
	searchCursor.value = 0;
});
watch(zoomPercent, value => {
	zoomInput.value = String(value);
});
watch(() => searchResults.value.length, count => {
	if (!count) searchCursor.value = 0;
	else searchCursor.value = Math.min(searchCursor.value, count - 1);
});

function fitCanvas() {
	canvasRef.value?.zoomToFit();
}

function applyZoomInput() {
	const parsed = Number.parseInt(zoomInput.value.replace(/[^0-9]/g, ""), 10);
	if (!Number.isFinite(parsed)) {
		zoomInput.value = String(zoomPercent.value);
		return;
	}
	const value = Math.min(300, Math.max(25, parsed));
	canvasRef.value?.setZoomPercent(value);
}

function selectZoomPreset(value: number | string) {
	const parsed = Number(value);
	if (Number.isFinite(parsed)) canvasRef.value?.setZoomPercent(parsed);
}

function handleSelectionChange(ids: string[]) {
	selectedObjectIds.value = [...new Set(ids)];
}

function handleCanvasClick() {
	selectedObjectIds.value = [];
	store.closeDrawer();
}

function handleNodesMoved(positions: Positions) {
	void store.saveNodePositions(positions);
}

function focusSearchResult(index: number) {
	if (!searchResults.value.length) return;
	const count = searchResults.value.length;
	searchCursor.value = (index + count) % count;
	const result = searchResults.value[searchCursor.value];
	if (!result) return;
	selectedObjectIds.value = [result.id];
	canvasRef.value?.centerOn(result.id);
}

async function handleLayoutCommand(direction: "horizontal" | "vertical") {
	const selected = selectedObjectIds.value.length > 1
		? store.visibleObjects.filter(object => selectedObjectIds.value.includes(object.id))
		: store.visibleObjects;
	const selectedIds = new Set(selected.map(object => object.id));
	const relations = store.visibleRelations.filter(relation => {
		const targets = Array.isArray(relation.targetType) ? relation.targetType : [relation.targetType];
		return selectedIds.has(relation.sourceType) && targets.some(target => selectedIds.has(target));
	});
	const positions = layeredLayout(selected, relations, direction);
	if (!Object.keys(positions).length) return;
	await store.saveNodePositions(positions);
}

async function alignSelection(action: AlignAction) {
	if (selectedObjectIds.value.length < 2) return;
	const selected = store.visibleObjects.filter(object => selectedObjectIds.value.includes(object.id));
	const positions: Positions = Object.fromEntries(selected.map(object => [object.id, { ...object.layout }]));
	const next = alignedPositions(positions, action);
	if (!Object.keys(next).length) return;
	await store.saveNodePositions(next);
}

function undoLayout() {
	void store.undoLayout();
}

function redoLayout() {
	void store.redoLayout();
}

function handleKeydown(event: KeyboardEvent) {
	const target = event.target as HTMLElement | null;
	const editing = !!target && ["INPUT", "TEXTAREA", "SELECT"].includes(target.tagName);
	if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "z" && !editing) {
		event.preventDefault();
		if (event.shiftKey) redoLayout();
		else undoLayout();
	}
	else if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "y" && !editing) {
		event.preventDefault();
		redoLayout();
	}
	else if (event.key === "Escape") {
		selectedObjectIds.value = [];
	}
}

async function toggleFullscreen() {
	if (!canvasHostRef.value) return;
	if (document.fullscreenElement) await document.exitFullscreen();
	else await canvasHostRef.value.requestFullscreen();
}

function handleFullscreenChange() {
	isFullscreen.value = document.fullscreenElement === canvasHostRef.value;
}

function handleDomainCreated(domain: DomainDef) {
	store.domains.push({ ...domain, color: "var(--ctff-brand-color)" });
	objectDraft.domainId = domain.domainId;
}

function resetDrafts() {
	if (store.drawerMode === "object" && store.currentObject) {
		Object.assign(objectDraft, {
			id: store.currentObject.id,
			name: store.currentObject.name,
			definition: store.currentObject.definition,
			domainId: store.currentObject.domainId
		});
		objectDetailDraft.value = clone(store.currentObject);
	}
	else if (store.drawerMode === "relation" && store.currentRelation) {
		for (const key of Object.keys(relationDraft)) delete relationDraft[key as keyof typeof relationDraft];
		Object.assign(relationDraft, clone(store.currentRelation));
	}
	else if (store.drawerMode === "create-object") {
		Object.assign(objectDraft, { id: "", name: "", definition: "", domainId: store.domains[0]?.domainId || "" });
	}
	else if (store.drawerMode === "create-relation") {
		const source = store.pendingRelationSource || "";
		Object.assign(relationDraft, {
			id: undefined,
			name: "",
			definition: "",
			sourceType: source,
			targetType: store.pendingRelationTarget || "",
			sourceCardinality: { min: 0, max: 1 },
			targetCardinality: { min: 0, max: "N" },
			compositionKind: "Association",
			domainId: store.objects.find(item => item.id === source)?.domainId || "",
			crossDomainDeclared: false,
			revision: 1
		});
	}
	ElMessage.info("已撤销未保存修改");
}

function openRelationFromObject(relationId: string) {
	relationReturnObjectId.value = store.currentObject?.id ?? null;
	store.openRelation(relationId);
}

function goBack() {
	if (relationReturnObjectId.value) {
		const target = relationReturnObjectId.value;
		relationReturnObjectId.value = null;
		void store.openObject(target);
	}
}

async function saveDrawer() {
	try {
		if (store.drawerMode === "object" || store.drawerMode === "create-object") {
			const error = overviewRef.value?.validate();
			if (error) {
				ElMessage.warning(error);
				activeTab.value = "overview";
				return;
			}
			const attributeError = attributesRef.value?.validate();
			if (attributeError) {
				ElMessage.warning(attributeError);
				activeTab.value = "attributes";
				return;
			}
			await store.saveObject({
				...objectDraft,
				...(objectDetailDraft.value
					? { attributes: objectDetailDraft.value.attributes, identitySpec: objectDetailDraft.value.identitySpec }
					: {})
			});
		}
		else {
			const error = relationEditorRef.value?.validate();
			if (error) {
				ElMessage.warning(error);
				return;
			}
			await store.saveRelation({ ...relationDraft });
		}
	}
	catch {
		/* API 已显示可定位错误 */
	}
}

async function deprecateObject() {
	await store.deprecateCurrentObject();
}

async function exportModel() {
	try {
		const model = await objectModelApi.fetchExport();
		const blob = new Blob([JSON.stringify(model, null, 2)], { type: "application/json;charset=utf-8" });
		const url = URL.createObjectURL(blob);
		const link = document.createElement("a");
		link.href = url;
		link.download = "object-model.json";
		link.click();
		URL.revokeObjectURL(url);
	}
	catch {
		ElMessage.error("导出失败");
	}
}
</script>

<script lang="ts">
import { defineComponent, h } from "vue";
const PlaceholderTab = defineComponent({
	props: { text: { type: String, required: true } },
	setup: props => () => h("div", { class: "placeholder-tab" }, props.text)
});
</script>

<style scoped>
:global(.ctff-workspace) {
	display: flex;
	flex-direction: column;
	overflow: hidden;
}

.object-model-page {
	/* 建模页占据工作区全部可用高度（外壳链路：app-shell → shell-main → workspace） */
	display: flex;
	flex: 1;
	flex-direction: column;
	box-sizing: border-box;
	width: calc(100% + var(--ctff-page-padding-inline, 0px) + var(--ctff-page-padding-inline, 0px));
	height: auto;
	min-height: 0;
	margin: calc(var(--ctff-page-padding-block, 0px) * -1) calc(var(--ctff-page-padding-inline, 0px) * -1);
}

.model-body {
	display: flex;
	flex: 1;
	min-height: 0;
}

.canvas-panel {
	display: flex;
	flex: 1;
	flex-direction: column;
	min-width: 0;
	min-height: 0;
	overflow: hidden;
	background: #f7f9fd;
}

.canvas-toolbar {
	display: flex;
	gap: 8px;
	align-items: center;
	padding: 8px 12px;
	border-bottom: 1px solid #e6edf5;
	background: rgba(255, 255, 255, 0.97);
	box-shadow: 0 1px 14px rgba(37, 58, 92, 0.06);
}

.stat-chip {
	display: inline-flex;
	gap: 6px;
	align-items: center;
	padding: 0 10px;
	height: 24px;
	border-radius: 12px;
	background: #f1efff;
	color: #554bb5;
	font-size: 12px;
	white-space: nowrap;
}

.status-dot {
	display: inline-block;
	width: 6px;
	height: 6px;
	background: #16a394;
	border-radius: 50%;
}

.domain-legend {
	display: inline-flex;
	gap: 6px;
	align-items: center;
	color: #718096;
	font-size: 12px;
	white-space: nowrap;
}

.domain-legend-mark {
	display: inline-block;
	width: 26px;
	height: 4px;
	background: linear-gradient(90deg, #635bff, #0ea5a0, #f97316);
	border-radius: 999px;
}

.domain-select {
	width: 160px;
}

.domain-option {
	display: inline-flex;
	gap: 6px;
	align-items: center;
}

.domain-dot {
	display: inline-block;
	width: 8px;
	height: 8px;
	border-radius: 50%;
}

.model-search {
	width: 220px;
}

.search-cluster {
	display: inline-flex;
	gap: 2px;
	align-items: center;
}

.search-results-trigger {
	box-sizing: border-box;
	height: 24px;
	padding: 0 7px;
	border: 1px solid #dbe5ef;
	border-radius: 4px;
	background: #f8fafc;
	color: #64748b;
	font-size: 11px;
	cursor: pointer;
}

.search-results-trigger:hover,
.search-results-trigger.is-empty {
	border-color: #f0b6b6;
	background: #fff5f5;
	color: #c45656;
}

.search-results-panel {
	display: flex;
	flex-direction: column;
	gap: 4px;
}

.search-results-header {
	display: flex;
	justify-content: space-between;
	padding: 2px 4px 6px;
	border-bottom: 1px solid #edf2f7;
	color: #334155;
	font-size: 12px;
	font-weight: 600;
}

.search-results-count,
.search-result-code {
	color: #94a3b8;
	font-size: 11px;
	font-weight: 400;
}

.search-result-item {
	display: flex;
	flex-direction: column;
	gap: 2px;
	align-items: flex-start;
	padding: 7px 8px;
	border: 0;
	border-radius: 5px;
	background: transparent;
	text-align: left;
	cursor: pointer;
}

.search-result-item:hover,
.search-result-item.is-current {
	background: #f1efff;
}

.search-result-name {
	color: #27364b;
	font-size: 12px;
	font-weight: 600;
}

.search-empty {
	padding: 12px 4px 4px;
	color: #94a3b8;
	font-size: 12px;
}

.toolbar-spacer {
	flex: 1;
}

.canvas-host {
	position: relative;
	flex: 1;
	min-height: 0;
	min-width: 0;
	overflow: hidden;
	background: #f7f9fd;
}

.canvas-bottom-left,
.canvas-bottom-right,
.selection-actions {
	position: absolute;
	display: flex;
	gap: 2px;
	align-items: center;
	padding: 0;
}

.canvas-bottom-left {
	bottom: 14px;
	left: 14px;
}

.canvas-bottom-right {
	right: 14px;
	bottom: 14px;
}

.canvas-bottom-left :deep(.el-button),
.canvas-bottom-right :deep(.el-button) {
	padding: 4px 5px;
	font-size: 11px;
	font-weight: 500;
	color: #71839a;
}

.canvas-bottom-left :deep(.el-button:hover),
.canvas-bottom-right :deep(.el-button:hover) {
	color: #28658f;
	background: rgba(255, 255, 255, 0.68);
}

.zoom-control {
	display: inline-flex;
	align-items: center;
	height: 28px;
	overflow: hidden;
	border: 1px solid rgba(191, 207, 223, 0.9);
	border-radius: 8px;
	background: rgba(255, 255, 255, 0.82);
	box-shadow: 0 4px 12px rgba(43, 76, 107, 0.08);
	backdrop-filter: blur(8px);
}

.zoom-button {
	display: inline-grid;
	place-items: center;
	width: 28px;
	height: 28px;
	padding: 0;
	border: 0;
	background: transparent;
	color: #58718b;
	cursor: pointer;
	transition: background 0.16s ease, color 0.16s ease;
}

.zoom-button:hover {
	background: #edf5fb;
	color: #24638e;
}

.zoom-dropdown {
	display: inline-flex;
	align-items: center;
	height: 28px;
}

.zoom-value-editor {
	display: inline-flex;
	gap: 1px;
	align-items: center;
	justify-content: center;
	box-sizing: border-box;
	width: 58px;
	height: 20px;
	border-right: 1px solid #e3ebf2;
	border-left: 1px solid #e3ebf2;
	color: #526b84;
	cursor: text;
}

.zoom-input {
	box-sizing: border-box;
	width: 35px;
	padding: 0;
	border: 0;
	outline: 0;
	background: transparent;
	color: #526b84;
	font: inherit;
	font-size: 11px;
	font-variant-numeric: tabular-nums;
	text-align: center;
}

.zoom-percent-sign {
	font-size: 11px;
	color: #8a9caf;
}

:deep(.zoom-dropdown-popper.el-popper) {
	padding: 4px;
	border-color: #d9e5ef;
	border-radius: 8px;
	box-shadow: 0 10px 24px rgba(42, 74, 105, 0.14);
}

:deep(.zoom-dropdown-popper .el-dropdown-menu) {
	display: grid;
	grid-template-columns: repeat(3, 1fr);
	gap: 2px;
	min-width: 164px;
	padding: 2px;
}

:deep(.zoom-dropdown-popper .el-dropdown-menu__item) {
	justify-content: center;
	height: 28px;
	padding: 0 6px;
	border-radius: 5px;
	color: #58718b;
	font-size: 11px;
}

:deep(.zoom-dropdown-popper .el-dropdown-menu__item:hover),
:deep(.zoom-dropdown-popper .el-dropdown-menu__item.is-active) {
	background: #edf7fe;
	color: #28658f;
}

.selection-actions {
	top: 14px;
	left: 50%;
	transform: translateX(-50%);
}

.selection-actions-label {
	padding: 0 3px;
	color: #554bb5;
	font-size: 12px;
	font-weight: 600;
	white-space: nowrap;
}

:deep(.canvas-bottom-left .el-button),
:deep(.canvas-bottom-right .el-button),
:deep(.selection-actions .el-button) {
	color: #64748b;
}

:deep(.canvas-bottom-left .el-button.is-text:hover),
:deep(.canvas-bottom-right .el-button.is-text:hover),
:deep(.selection-actions .el-button.is-text:hover),
:deep(.canvas-bottom-right .el-button.is-active) {
	color: var(--ctff-brand-color);
	background: var(--ctff-brand-color-background);
}

:deep(.canvas-bottom-left .el-button--primary),
:deep(.canvas-bottom-left .el-button--primary:hover) {
	color: #ffffff;
	background: var(--ctff-brand-color);
	border-color: var(--ctff-brand-color);
}

.canvas-error {
	position: absolute;
	inset: 0;
	display: grid;
	place-items: center;
	background: rgba(247, 249, 253, 0.9);
}

:deep(.canvas-toolbar .el-button) {
	color: #64748b;
}

:deep(.canvas-toolbar .el-button:hover) {
	color: #554bb5;
	background: #f1efff;
}

:deep(.canvas-toolbar .el-input__wrapper),
:deep(.canvas-toolbar .el-select__wrapper) {
	background: #ffffff;
	box-shadow: inset 0 0 0 1px #dbe5ef;
}

:deep(.canvas-toolbar .el-input__inner),
:deep(.canvas-toolbar .el-select__selected-item),
:deep(.canvas-toolbar .el-input__prefix),
:deep(.canvas-toolbar .el-input__suffix) {
	color: #3a4961;
}

:deep(.canvas-toolbar .el-button--primary.is-plain) {
	color: var(--ctff-brand-color);
	background: var(--ctff-brand-color-background);
	border-color: var(--ctff-brand-color-disabled-border);
}

:deep(.canvas-toolbar .el-button--primary:not(.is-plain)) {
	color: #ffffff !important;
	background: var(--ctff-brand-color);
	border-color: var(--ctff-brand-color);
	box-shadow: 0 4px 12px rgb(var(--ctff-brand-color-rgb) / 22%);
}

:deep(.canvas-toolbar .el-button--primary:not(.is-plain):hover),
:deep(.canvas-toolbar .el-button--primary:not(.is-plain):focus-visible) {
	color: #ffffff !important;
	background: var(--ctff-brand-color-hover);
	border-color: var(--ctff-brand-color-hover);
}

.drawer-header {
	display: flex;
	gap: 8px;
	align-items: center;
	width: 100%;
	margin-right: -24px;
}

.back-button {
	font-weight: 600;
}

.drawer-title-text {
	flex: 1;
	overflow: hidden;
	font-size: 16px;
	font-weight: 600;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.drawer-footer {
	display: flex;
	gap: 8px;
	align-items: center;
	width: 100%;
}

.drawer-status {
		display: flex;
		flex-direction: column;
		gap: 8px;
		margin-bottom: 12px;
}

.save-error-alert {
		width: 100%;
		box-sizing: border-box;
}

.save-error-body {
		display: flex;
		flex-wrap: wrap;
		gap: 4px 8px;
		align-items: center;
}

.save-error-details {
		width: 100%;
		color: var(--ctff-text-color-secondary);
		font-size: 12px;
}

.footer-spacer {
	flex: 1;
}

.model-tabs {
	min-height: 520px;
}

:deep(.placeholder-tab) {
	padding: 40px 10px;
	color: var(--ctff-text-color-tertiary);
	text-align: center;
}
</style>
