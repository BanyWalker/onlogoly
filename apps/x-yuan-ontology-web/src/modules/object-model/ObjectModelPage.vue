<template>
	<div class="object-model-page">
		<div class="model-body">
			<section class="canvas-panel">
				<div class="canvas-toolbar">
					<ElButton size="small" type="primary" plain :icon="Plus" @click="store.startCreateObject()">新建对象</ElButton>
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
										<SearchHighlight class="search-result-name" :text="result.name" :query="store.searchText" />
										<SearchHighlight class="search-result-code" :text="result.id" :query="store.searchText" />
										<SearchHighlight v-if="matchingIdentityNames(result.identityAttrNames).length" class="search-result-code" :text="matchingIdentityNames(result.identityAttrNames).join(' · ')" :query="store.searchText" />
									</button>
									<div v-if="!searchResults.length" class="search-empty">未找到匹配对象，请尝试对象名称、编码或标识属性。</div>
								</div>
							</ElPopover>
							<div class="search-navigation" role="group" aria-label="切换搜索结果">
								<ElButton size="small" text :icon="ArrowUp" :disabled="!searchResults.length" aria-label="上一个搜索结果" @click="focusSearchResult(searchCursor - 1)" />
								<ElButton size="small" text :icon="ArrowDown" :disabled="!searchResults.length" aria-label="下一个搜索结果" @click="focusSearchResult(searchCursor + 1)" />
							</div>
						</template>
					</div>
					<span class="toolbar-spacer" />
					<ElDropdown trigger="click" @command="handleLayoutCommand">
						<ElButton size="small" text :icon="Sort">自动布局<ElIcon class="el-icon--right"><ArrowDown /></ElIcon></ElButton>
						<template #dropdown>
							<ElDropdownMenu>
								<ElDropdownItem command="horizontal">横向布局</ElDropdownItem>
								<ElDropdownItem command="vertical">纵向布局</ElDropdownItem>
							</ElDropdownMenu>
						</template>
					</ElDropdown>
					<ElButton size="small" text :icon="Refresh" :loading="store.loading" @click="store.loadGraph()">刷新</ElButton>
					<div class="layout-history-actions" role="group" aria-label="布局历史">
						<ElButton size="small" text :disabled="!store.layoutUndoStack.length || store.layoutBusy" title="撤销布局（Ctrl/Cmd+Z）" @click="undoLayout">
							<template #icon><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m9 4-5 5 5 5M4 9h9a6 6 0 0 1 0 12h-3" /></svg></template>
							撤销
						</ElButton>
						<ElButton size="small" text :disabled="!store.layoutRedoStack.length || store.layoutBusy" title="重做布局（Ctrl/Cmd+Shift+Z）" @click="redoLayout">
							<template #icon><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m15 4 5 5-5 5M20 9h-9a6 6 0 0 0 0 12h3" /></svg></template>
							重做
						</ElButton>
					</div>
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
						:search-text="store.searchText"
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
					<div class="canvas-footer">
						<div class="canvas-status-bar">
							<span class="stat-chip" aria-live="polite">{{ store.visibleObjects.length }} 对象 · {{ store.visibleRelations.length }} 关系</span>
							<div class="domain-switcher" role="group" aria-label="领域筛选">
								<button
									v-for="domain in store.domains"
									:key="domain.domainId"
									type="button"
									class="domain-filter-button"
									:class="{ 'is-active': !store.domainFilter || store.domainFilter === domain.domainId }"
									:aria-pressed="!store.domainFilter || store.domainFilter === domain.domainId"
									:title="`${domain.name}（${domain.objectCount ?? 0} 个对象）`"
									@click="store.domainFilter = store.domainFilter === domain.domainId ? null : domain.domainId"
								>
									<i class="domain-dot" :style="{ background: store.domainColorMap.get(domain.domainId) }" />{{ domain.name }}
								</button>
							</div>
							<div class="relation-legend" role="group" aria-label="画布图例">
								<span class="legend-item"><i class="legend-arrow" aria-hidden="true" />有向关系</span>
								<span class="legend-item"><i class="legend-arrow is-highlighted" aria-hidden="true" />选中高亮链路</span>
								<span class="legend-item" title="已废弃对象默认从画布隐藏"><i class="legend-deprecated" aria-hidden="true" />已废弃对象</span>
							</div>
						</div>
					<div class="canvas-bottom-right">
						<div class="zoom-control" role="group" aria-label="画布缩放">
							<button type="button" class="zoom-button" aria-label="缩小" @click="canvasRef?.zoomOut()"><ElIcon><ZoomOut /></ElIcon></button>
							<ElDropdown placement="top" trigger="click" popper-class="zoom-dropdown-popper" @command="selectZoomPreset">
								<div class="zoom-value-editor" role="combobox" tabindex="0" aria-haspopup="menu" aria-label="选择缩放比例">
									<input
										v-model="zoomInput"
										class="zoom-input"
										type="text"
										inputmode="numeric"
										aria-label="缩放比例"
										@mousedown="focusZoomInput"
										@click="focusZoomInput"
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
						<ElButton size="small" text class="fit-canvas-button" :icon="Aim" @click="fitCanvas">适应画布</ElButton>
						<ElButton size="small" text :icon="FullScreen" @click="toggleFullscreen">{{ isFullscreen ? "退出全屏" : "全屏" }}</ElButton>
					</div>
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
							:attributes="objectDetailDraft?.attributes ?? []"
							:relations="store.relations"
							:revision="store.currentObject?.revision ?? 1"
							:completeness="store.currentObject?.completeness ?? 'draft'"
							@domain-created="handleDomainCreated"
						/>
					</ElTabPane>
					<ElTabPane label="属性" name="attributes" :disabled="store.drawerMode === 'create-object'">
						<AttributesEditor v-if="objectDetailDraft" ref="attributesRef" :object="objectDetailDraft" :objects="store.objects" :relations="store.relations" />
					</ElTabPane>
					<ElTabPane label="关系" name="object-relations" :disabled="store.drawerMode === 'create-object'">
						<RelationList v-if="store.currentObject" :object-id="store.currentObject.id" :relations="store.relations" @open="openRelationFromObject" />
					</ElTabPane>
					<ElTabPane label="动作" name="actions" :disabled="store.drawerMode === 'create-object'">
						<ActionsEditor
							v-if="objectDetailDraft"
							v-model="objectActions"
							:services="actionServices"
							:loading="actionServicesLoading"
						/>
					</ElTabPane>
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
	Aim,
	ArrowDown,
	ArrowLeft,
	ArrowUp,
	Download,
	FullScreen,
	Plus,
	Refresh,
	Search,
	Sort,
	ZoomIn,
	ZoomOut
} from "@element-plus/icons-vue";
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";

import { objectModelApi } from "./api";
import { alignedPositions, layeredLayout, type AlignAction, type Positions } from "./canvas-layout";
import AttributesEditor from "./components/tabs/AttributesEditor.vue";
import ActionsEditor from "./components/tabs/ActionsEditor.vue";
import ObjectCanvas from "./components/ObjectCanvas.vue";
import OverviewTab, { type OverviewDraft } from "./components/tabs/OverviewTab.vue";
import RelationEditor from "./components/tabs/RelationEditor.vue";
import RelationList from "./components/tabs/RelationList.vue";
import { useObjectModelStore } from "./store";
import SearchHighlight from "./components/SearchHighlight.vue";
import { apiServiceApi } from "../service-integration/api";
import { mcpApi } from "../service-integration/mcp-api";
import type { ApiService } from "../service-integration/types";
import type { McpServer } from "../service-integration/mcp-types";
import type { ActionServiceOption, ActionServiceType, DomainDef, ObjectAction, ObjectTypeDetail, RelationDetail } from "./types";

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
const actionServices = ref<Record<ActionServiceType, ActionServiceOption[]>>({ API: [], MCP: [], FUNCTION: [] });
const actionServicesLoading = ref(false);
const selectedObjectIds = ref<string[]>([]);
const zoomPercent = ref(100);
const zoomInput = ref("100");
const zoomPresets = [50, 75, 100, 125, 150, 200];
const isFullscreen = ref(false);
const searchCursor = ref(0);

const objectActions = computed<ObjectAction[]>({
	get: () => objectDetailDraft.value?.actions ?? [],
	set: value => {
		if (objectDetailDraft.value) objectDetailDraft.value.actions = value;
	}
});

const searchResults = computed(() => {
	const keyword = store.searchText.trim().toLowerCase();
	if (!keyword) return [];
	return store.visibleObjects.filter(object =>
		object.name.toLowerCase().includes(keyword) ||
		object.id.toLowerCase().includes(keyword) ||
		object.identityAttrNames.some(name => name.toLowerCase().includes(keyword))
	);
});

function matchingIdentityNames(names: string[]) {
	return names.filter(name => name.toLowerCase().includes(store.searchText.trim().toLowerCase()));
}

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
			identitySpec: objectDetailDraft.value.identitySpec,
			actions: objectDetailDraft.value.actions ?? []
		}) !== stable({
			overview: {
				id: store.currentObject.id,
				name: store.currentObject.name,
				definition: store.currentObject.definition,
				domainId: store.currentObject.domainId
			},
			attributes: store.currentObject.attributes,
			identitySpec: store.currentObject.identitySpec,
			actions: store.currentObject.actions ?? []
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
	void loadActionServices();
	window.addEventListener("keydown", handleKeydown);
	document.addEventListener("fullscreenchange", handleFullscreenChange);
});

async function loadActionServices() {
	actionServicesLoading.value = true;
	const [apiResult, mcpResult] = await Promise.allSettled([apiServiceApi.list(), mcpApi.list()]);
	const apiServices = apiResult.status === "fulfilled" ? apiResult.value : [];
	const mcpServices = mcpResult.status === "fulfilled" ? mcpResult.value : [];
	actionServices.value = {
		API: apiServices.map(toApiActionService),
		MCP: mcpServices.map(toMcpActionService),
		FUNCTION: []
	};
	actionServicesLoading.value = false;
}

function toApiActionService(service: ApiService): ActionServiceOption {
	return {
		id: service.id,
		name: service.name,
		type: "API",
		description: service.definition,
		detail: service.baseUrl
	};
}

function toMcpActionService(service: McpServer): ActionServiceOption {
	return {
		id: service.id,
		name: service.name,
		type: "MCP",
		description: service.definition,
		detail: service.endpointUrl
	};
}

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

function focusZoomInput(event: MouseEvent) {
	(event.currentTarget as HTMLInputElement | null)?.focus();
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
					? {
						attributes: objectDetailDraft.value.attributes,
						identitySpec: objectDetailDraft.value.identitySpec,
						actions: objectDetailDraft.value.actions ?? []
					}
					: {})
			}, activeTab.value === "actions" ? "动作关联成功" : "保存成功");
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
	flex-wrap: wrap;
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
	background: #f0f5fb;
	color: #58718b;
	font-size: 12px;
	white-space: nowrap;
}

.layout-history-actions {
	display: inline-flex;
	gap: 2px;
	align-items: center;
	padding-left: 8px;
	border-left: 1px solid #e6edf5;
}

.layout-history-actions :deep(.el-button + .el-button) {
	margin-left: 0;
}

.domain-dot {
	display: inline-block;
	flex-shrink: 0;
	width: 10px;
	height: 10px;
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

.search-navigation {
	display: inline-flex;
	align-items: center;
	padding: 1px;
	border: 1px solid #e6edf5;
	border-radius: 5px;
	background: #f8fafc;
}

.search-navigation :deep(.el-button) {
	width: 26px;
	padding: 4px;
}

.search-navigation :deep(.el-button + .el-button) {
	margin-left: 0;
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
	container-type: inline-size;
}

.selection-actions {
	position: absolute;
	display: flex;
	gap: 2px;
	align-items: center;
	padding: 0;
}

.canvas-footer {
	position: absolute;
	right: 14px;
	bottom: 12px;
	left: 14px;
	display: grid;
	grid-template-columns: minmax(250px, 1fr) minmax(0, max-content) minmax(250px, 1fr);
	gap: 10px;
	align-items: center;
	pointer-events: none;
}

.canvas-status-bar {
	display: flex;
	grid-column: 2;
	flex-wrap: wrap;
	gap: 10px 14px;
	align-items: center;
	justify-content: center;
	box-sizing: border-box;
	max-width: 100%;
	padding: 7px 12px;
	border: 1px solid rgb(219 229 239 / 54%);
	border-radius: 10px;
	background: rgb(251 252 254 / 94%);
	box-shadow: 0 4px 16px rgb(43 76 107 / 3%);
	backdrop-filter: blur(8px);
	pointer-events: auto;
}

.domain-switcher {
	display: flex;
	gap: 4px;
	align-items: center;
	min-width: 0;
	overflow-x: auto;
}

.domain-filter-button {
	display: inline-flex;
	flex-shrink: 0;
	gap: 7px;
	align-items: center;
	padding: 4px 7px;
	border: 1px solid transparent;
	border-radius: 5px;
	background: transparent;
	color: #58718b;
	font: inherit;
	font-size: 12px;
	white-space: nowrap;
	cursor: pointer;
}

.domain-filter-button.is-active {
	background: rgb(244 248 252 / 76%);
}

.domain-filter-button:not(.is-active) {
	color: #94a3b8;
}

.domain-filter-button:not(.is-active) .domain-dot {
	opacity: 0.45;
}

.domain-filter-button:hover {
	background: rgb(239 245 250 / 84%);
}

.domain-filter-button:focus-visible {
	outline: 2px solid var(--ctff-brand-color);
	outline-offset: -2px;
}

.relation-legend {
	display: flex;
	flex-wrap: wrap;
	gap: 10px 18px;
	align-items: center;
	justify-content: center;
}

.legend-item {
	display: inline-flex;
	gap: 7px;
	align-items: center;
	color: #58718b;
	font-size: 12px;
	white-space: nowrap;
}

.legend-arrow {
	position: relative;
	display: inline-block;
	width: 24px;
	height: 2px;
	background: #a9b8cc;
	color: #a9b8cc;
}

.legend-arrow::after {
	position: absolute;
	top: -4px;
	right: -1px;
	width: 0;
	height: 0;
	border-top: 5px solid transparent;
	border-bottom: 5px solid transparent;
	border-left: 8px solid currentColor;
	content: "";
}

.legend-arrow.is-highlighted {
	background: #50bfa7;
	color: #50bfa7;
}

.legend-deprecated {
	width: 20px;
	height: 16px;
	border: 1px dashed #d8e3ed;
	border-radius: 5px;
	background: rgb(251 252 254 / 78%);
}

.canvas-bottom-right {
	display: flex;
	grid-column: 3;
	gap: 2px;
	align-items: center;
	justify-self: end;
	white-space: nowrap;
	pointer-events: auto;
}

@container (max-width: 1000px) {
	.canvas-footer {
		grid-template-columns: minmax(0, 1fr) auto;
	}

	.canvas-status-bar {
		grid-column: 1;
		width: fit-content;
		max-width: calc(100% - 248px);
		justify-self: start;
	}

	.canvas-bottom-right {
		/* 小屏时脱离状态栏的网格排布，始终贴着画布右下角。 */
		position: absolute;
		right: 0;
		bottom: 0;
		grid-column: 2;
	}
}

.canvas-bottom-right .fit-canvas-button {
	margin-left: 8px;
}

.canvas-bottom-right :deep(.el-button) {
	padding: 4px 4px;
	font-size: 11px;
	font-weight: 500;
	color: #71839a;
}

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
	width: 48px;
	height: 20px;
	border-right: 1px solid #e3ebf2;
	border-left: 1px solid #e3ebf2;
	color: #526b84;
	cursor: text;
}

.zoom-input {
	box-sizing: border-box;
	width: 28px;
	padding: 0;
	border: 0;
	outline: 0;
	background: transparent;
	color: #526b84;
	font: inherit;
	font-size: 10px;
	font-variant-numeric: tabular-nums;
	text-align: center;
}

.zoom-percent-sign {
	font-size: 10px;
	color: #8a9caf;
}

:deep(.zoom-dropdown-popper.el-popper) {
	padding: 4px;
	border-color: #d9e5ef;
	border-radius: 8px;
	box-shadow: 0 10px 24px rgba(42, 74, 105, 0.14);
}

:deep(.zoom-dropdown-popper .el-dropdown-menu) {
	display: flex;
	flex-direction: column;
	gap: 0;
	min-width: 112px;
	padding: 4px;
}

:deep(.zoom-dropdown-popper .el-dropdown-menu__item) {
	justify-content: center;
	box-sizing: border-box;
	min-height: 32px;
	height: 32px;
	padding: 8px;
	line-height: 16px;
	border-radius: 5px;
	color: #58718b;
	font-size: 10px;
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

:deep(.canvas-bottom-right .el-button),
:deep(.selection-actions .el-button) {
	color: #64748b;
}

:deep(.canvas-bottom-right .el-button.is-text:hover),
:deep(.selection-actions .el-button.is-text:hover),
:deep(.canvas-bottom-right .el-button.is-active) {
	color: var(--ctff-brand-color);
	background: var(--ctff-brand-color-background);
}

.canvas-error {
	position: absolute;
	inset: 0;
	display: grid;
	place-items: center;
	background: rgba(247, 249, 253, 0.9);
}

:deep(.canvas-toolbar .el-button.is-text) {
	color: var(--ctff-text-color-secondary);
}

:deep(.canvas-toolbar .el-button.is-text:not(.is-disabled):hover),
:deep(.canvas-toolbar .el-button.is-text:not(.is-disabled):focus-visible) {
	color: var(--ctff-brand-color);
	background: var(--ctff-brand-color-background);
}

:deep(.canvas-toolbar .el-button.is-text:not(.is-disabled):active),
:deep(.canvas-toolbar .el-button.is-text:not(.is-disabled).is-active) {
	color: var(--ctff-brand-color-active);
	background: var(--ctff-brand-color-background);
}

:deep(.canvas-toolbar .el-button.is-text.is-disabled) {
	color: var(--ctff-text-color-quaternary);
	background: transparent;
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

:deep(.canvas-toolbar .el-button--primary.is-plain:not(.is-disabled)) {
	color: var(--ctff-brand-color);
	background: var(--ctff-brand-color-background);
	border-color: var(--ctff-brand-color-disabled-border);
}

:deep(.canvas-toolbar .el-button--primary.is-plain:not(.is-disabled):hover),
:deep(.canvas-toolbar .el-button--primary.is-plain:not(.is-disabled):focus-visible) {
	color: var(--ctff-brand-color-hover);
	border-color: var(--ctff-brand-color-hover);
}

:deep(.canvas-toolbar .el-button--primary.is-plain:not(.is-disabled):active) {
	color: var(--ctff-brand-color-active);
	border-color: var(--ctff-brand-color-active);
}

:deep(.canvas-toolbar .el-button--primary:not(.is-plain, .is-disabled)) {
	color: var(--ctff-solid-text-color);
	background: var(--ctff-brand-color);
	border-color: var(--ctff-brand-color);
	box-shadow: 0 4px 12px rgb(var(--ctff-brand-color-rgb) / 22%);
}

:deep(.canvas-toolbar .el-button--primary:not(.is-plain, .is-disabled):hover),
:deep(.canvas-toolbar .el-button--primary:not(.is-plain, .is-disabled):focus-visible) {
	background: var(--ctff-brand-color-hover);
	border-color: var(--ctff-brand-color-hover);
}

:deep(.canvas-toolbar .el-button--primary:not(.is-plain, .is-disabled):active) {
	background: var(--ctff-brand-color-active);
	border-color: var(--ctff-brand-color-active);
}

.drawer-header {
	display: flex;
	gap: 8px;
	align-items: center;
	width: 100%;
	margin-right: -24px;
}

:deep(.model-drawer .el-drawer__header) {
	padding: 18px 24px 10px;
	margin-bottom: 0;
}

:deep(.model-drawer .el-drawer__body) {
	padding: 0 24px 20px;
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

</style>
