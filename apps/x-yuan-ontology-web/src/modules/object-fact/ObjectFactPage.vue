<template>
	<div class="object-fact-page">
		<section class="fact-shell" aria-label="对象实例建模工作区">
			<div class="fact-toolbar">
				<ElButton size="small" type="primary" :icon="Upload" :disabled="!store.writable || store.loading" @click="openImport"
					>导入拓扑</ElButton
				>
				<ElButton size="small" plain :icon="Plus" :disabled="!store.writable || store.loading" @click="startCreate"
					>新建实例</ElButton
				>
				<ElButton
					size="small"
					:type="linkMode ? 'primary' : undefined"
					:plain="linkMode"
					:disabled="!store.writable || !store.catalog.instanceCount"
					@click="toggleLink"
					>连线</ElButton
				>
				<ElButton
					size="small"
					plain
					:icon="CircleCheck"
					:disabled="!store.writable || busy || store.loading"
					@click="moreAction('check')"
					>一致性检查</ElButton
				>
				<ElInput v-model="store.filters.q" size="small" clearable class="fact-search" placeholder="搜索业务标识 / 名称…"
					><template #prefix
						><ElIcon><Search /></ElIcon></template
				></ElInput>
				<ElSelect
					v-model="store.filters.domain"
					size="small"
					clearable
					filterable
					class="fact-domain-filter"
					placeholder="全部领域"
					><ElOption v-for="domain in store.catalog.domains" :key="domain.domainId" :value="domain.domainId" :label="domain.name"
				/></ElSelect>
				<ElSelect v-model="store.filters.mark" size="small" clearable class="fact-mark-filter" placeholder="一致性标记"
					><ElOption label="待复核" value="pendingReview" /><ElOption label="引用失效" value="invalidRef"
				/></ElSelect>
				<span class="toolbar-spacer" />
				<ElDropdown trigger="click" @command="applyLayout"
					><ElButton size="small" text :icon="Sort">自动布局</ElButton
					><template #dropdown
						><ElDropdownMenu
							><ElDropdownItem command="horizontal">横向布局</ElDropdownItem
							><ElDropdownItem command="vertical">纵向布局</ElDropdownItem></ElDropdownMenu
						></template
					></ElDropdown
				>
				<ElButton size="small" text :icon="Refresh" :loading="store.loading" @click="refresh">刷新</ElButton>
				<ElButton size="small" text :icon="Clock" @click="moreAction('history')">操作记录</ElButton>
			</div>
			<ElAlert
				v-if="linkMode"
				:title="
					linkSource
						? `已选源实例 ${nodeName(linkSource)}，点击目标实例；Esc 退出`
						: '点击源实例，再点击目标实例；也可像对象建模一样从节点端口拖动连线'
				"
				type="info"
				:closable="false"
				class="fact-mode-banner"
			/>
			<ElAlert
				v-if="store.focus"
				:title="`邻域聚焦 · ${nodeName(store.focus)}`"
				type="info"
				:closable="false"
				class="fact-mode-banner"
				><ElSelect v-model="store.hops" size="small" class="fact-hops" @change="store.refresh"
					><ElOption v-for="hop in [1, 2, 3]" :key="hop" :value="hop" :label="`${hop} 跳邻域`" /></ElSelect
				><ElButton size="small" text @click="clearFocus">返回全局拓扑</ElButton></ElAlert
			>
			<div class="fact-body">
				<FactTypeList
					:types="store.typeItems"
					:selected-types="store.filters.types"
					:visible-count="store.graph.nodes.length"
					:total-count="store.catalog.instanceCount"
					@select="selectTypes"
				/>
				<div ref="canvasHost" v-loading="store.loading" class="fact-canvas-host">
					<ObjectCanvas
						:key="store.role"
						ref="canvas"
						:objects="store.objects"
						:relations="store.relations"
						:edge-color="FACT_EDGE_COLOR"
						:domain-color-map="store.colors"
						:domain-name-map="store.domains"
						:highlighted-ids="store.highlightedIds"
						:search-text="store.filters.q"
						:selected-object-ids="selectedIds"
						:selected-object-id="store.selected?.instanceId"
						:selected-relation-id="selectedRelation"
						:read-only="!store.writable"
						allow-parallel
						@select-object="canvasPick"
						@select-relation="pickEdge"
						@create-relation="beginLink"
						@nodes-moved="moveNodes"
						@selection-change="selectedIds = $event"
						@zoom-change="zoom = $event"
						@canvas-click="blankClick"
					/>
					<div v-if="store.error" class="fact-canvas-overlay">
						<ElResult icon="warning" title="实例事实暂时无法加载" :sub-title="store.error.message"
							><template #extra><ElButton type="primary" plain @click="refresh">重新加载</ElButton></template></ElResult
						>
					</div>
					<div v-else-if="!store.graph.nodes.length && !store.loading" class="fact-canvas-overlay is-empty">
						<ElEmpty
							:description="store.catalog.instanceCount ? '当前筛选范围没有实例' : '导入拓扑或新建第一个对象实例'"
							:image-size="90"
							><ElButton v-if="store.writable" type="primary" plain @click="startCreate">新建实例</ElButton
							><ElButton v-if="store.writable" plain @click="openImport">导入拓扑</ElButton></ElEmpty
						>
					</div>
					<ElAlert
						v-if="store.graph.truncated"
						:title="`当前展示 ${store.graph.nodes.length} / ${store.graph.total} 个实例，请按类型、领域筛选或选择节点聚焦邻域`"
						type="warning"
						:closable="false"
						class="fact-truncated"
					/>
					<div class="fact-canvas-footer">
						<div class="fact-status">
							<span>{{ store.graph.nodes.length }} 实例 · {{ store.graph.edges.length }} 关系</span
							><button
								v-for="type in store.typeItems"
								:key="type.id"
								type="button"
								:class="{ 'is-inactive': store.filters.types.length && !store.filters.types.includes(type.id) }"
								:aria-pressed="!store.filters.types.length || store.filters.types.includes(type.id)"
								@click="selectType(type.id)"
							>
								<i :style="{ background: type.color }" />{{ type.name }}
							</button>
						</div>
						<div class="fact-viewport-controls">
							<ElButton size="small" text :icon="ZoomOut" aria-label="缩小" @click="canvas?.zoomOut()" /><span>{{ zoom }}%</span
							><ElButton size="small" text :icon="ZoomIn" aria-label="放大" @click="canvas?.zoomIn()" /><ElButton
								size="small"
								text
								:icon="Aim"
								@click="canvas?.zoomToFit()"
								>适应画布</ElButton
							><ElButton size="small" text :icon="FullScreen" @click="toggleFullscreen">{{
								isFullscreen ? "退出全屏" : "全屏"
							}}</ElButton>
						</div>
					</div>
				</div>
			</div>
		</section>
		<FactEditorDrawer
			ref="editor"
			v-model="drawerVisible"
			:creating="creating"
			:detail="store.selected"
			:catalog="store.catalog"
			:writable="store.writable"
			:saving="busy"
			:loading="store.detailLoading"
			:error="saveError"
			@save="save"
			@delete="deleteInstance"
			@reload="reloadDetail"
			@select="pick"
			@focus="focusInstance"
		/>
		<ElDialog
			v-model="importVisible"
			:append-to="overlayTarget"
			title="导入完整实例拓扑"
			width="760px"
			:close-on-click-modal="false"
		>
			<div class="fact-upload-zone" @dragover.prevent @drop.prevent="dropFile">
				<ElIcon :size="32"><Upload /></ElIcon>
				<p>{{ importFile?.name ?? "选择或拖拽 UTF-8 JSON 拓扑文件到这里" }}</p>
				<ElButton plain :disabled="busy" @click="fileInput?.click()">选择文件</ElButton
				><input ref="fileInput" type="file" accept=".json,application/json" hidden @change="chooseFile" />
			</div>
			<p class="fact-help">
				文件须包含实例、关系和来源标识，关系通过文件内 instanceKey 互相引用。重复业务标识将整批拒绝；更新整批事实前须单独清除。
			</p>
			<ElAlert v-if="importError" type="error" :title="importError" :closable="false" />
			<ElAlert
				v-if="importResult"
				:type="importResult.result === 'ACCEPTED' ? 'success' : 'error'"
				:title="
					importResult.result === 'ACCEPTED'
						? `导入成功：${importResult.instanceCount} 实例 / ${importResult.relationCount} 关系 · ${importResult.batchId}`
						: `导入拒绝：${importResult.errors.length} 处错误，实例事实未修改 · ${importResult.batchId}`
				"
				:closable="false"
			/>
			<ElTable v-if="importResult?.errors.length" :data="importResult.errors" max-height="320" size="small"
				><ElTableColumn prop="instanceKey" label="实例" width="100" /><ElTableColumn
					prop="fieldPath"
					label="字段路径"
					width="170" /><ElTableColumn prop="rule" label="规则" width="170" /><ElTableColumn prop="message" label="说明"
			/></ElTable>
			<template #footer
				><ElButton :disabled="busy" @click="importVisible = false">关闭</ElButton
				><ElButton
					type="primary"
					:disabled="!importFile || !store.writable || importResult?.result === 'ACCEPTED'"
					:loading="busy"
					@click="submitImport"
					>校验并导入</ElButton
				></template
			>
		</ElDialog>
		<ElDialog v-model="linkVisible" :append-to="overlayTarget" title="新增实例关系" width="480px" :close-on-click-modal="false"
			><p>{{ nodeName(linkPair.source) }} → {{ nodeName(linkPair.target) }}</p>
			<ElSelect v-model="linkRelation" :append-to="overlayTarget" placeholder="按两端对象类型选择关系"
				><ElOption v-for="relation in linkOptions" :key="relation.id" :value="relation.id" :label="relation.name" /></ElSelect
			><ElAlert v-if="linkError" :title="linkError.message" type="error" :closable="false"
				><div v-for="(item, index) in linkError.details ?? []" :key="index">{{ item.message }}</div></ElAlert
			><template #footer
				><ElButton :disabled="busy" @click="linkVisible = false">取消</ElButton
				><ElButton type="primary" :disabled="!linkRelation || !store.writable" :loading="busy" @click="submitLink"
					>保存关系</ElButton
				></template
			></ElDialog
		>
		<ElDialog v-model="protectedVisible" :append-to="overlayTarget" title="实例删除保护" width="580px"
			><ElAlert title="该实例仍有关联关系，普通删除已拒绝。请先明确解除引用。" type="warning" :closable="false" /><ElTable
				:data="protectedRefs"
				max-height="280"
				><ElTableColumn prop="relationName" label="关系" /><ElTableColumn prop="sourceInstanceId" label="源实例" /><ElTableColumn
					prop="targetInstanceId"
					label="目标实例"
			/></ElTable>
			<p class="fact-help">
				可以在关系页签逐条解除。若被删实例存在必选关系，可将解除引用与删除作为一次整体操作，按删除后剩余拓扑校验。
			</p>
			<template #footer
				><ElButton @click="protectedVisible = false">返回编辑</ElButton
				><ElButton type="danger" plain :disabled="!store.writable" @click="deleteAfterUnlink">解除引用并删除</ElButton></template
			></ElDialog
		>
		<ElDrawer v-model="historyVisible" :append-to="overlayTarget" title="实例操作记录" size="820px"
			><ElTable v-loading="historyLoading" :data="history"
				><ElTableColumn type="expand"
					><template #default="{ row }">
						<pre class="fact-record-detail">{{ JSON.stringify(row, null, 2) }}</pre>
					</template></ElTableColumn
				><ElTableColumn prop="operationId" label="操作标识" width="130" /><ElTableColumn label="操作" width="100"
					><template #default="{ row }">{{ recordNames[row.recordKind as FactRecord["recordKind"]] }}</template></ElTableColumn
				><ElTableColumn prop="instanceRef" label="实例" width="130" /><ElTableColumn
					prop="result"
					label="结果"
					width="100" /><ElTableColumn prop="operatedBy" label="操作者" /><ElTableColumn
					prop="operatedAt"
					label="时间"
					width="210" /></ElTable
			><ElPagination
				v-if="historyTotal > 30"
				v-model:current-page="historyPage"
				layout="prev, pager, next"
				:page-size="30"
				:total="historyTotal"
				@current-change="loadHistory"
		/></ElDrawer>
	</div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { onBeforeRouteLeave, useRoute, useRouter } from "vue-router";
import {
	Aim,
	CircleCheck,
	Clock,
	FullScreen,
	Plus,
	Refresh,
	Search,
	Sort,
	Upload,
	ZoomIn,
	ZoomOut
} from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import ObjectCanvas from "@/modules/object-model/components/ObjectCanvas.vue";
import type { Positions } from "@/modules/object-model/canvas-layout";
import FactEditorDrawer from "./components/FactEditorDrawer.vue";
import FactTypeList from "./components/FactTypeList.vue";
import { factApi } from "./api";
import { matchingRelations } from "./form";
import { useCanvasFullscreen } from "./fullscreen";
import { FACT_EDGE_COLOR } from "./presentation";
import { useObjectFactStore } from "./store";
import type { FactCreate, FactEdit, FactError, FactImport, FactRecord, FactReference } from "./types";

const store = useObjectFactStore(),
	route = useRoute(),
	router = useRouter();
const canvas = ref<InstanceType<typeof ObjectCanvas>>(),
	editor = ref<InstanceType<typeof FactEditorDrawer>>(),
	canvasHost = ref<HTMLElement>();
const {
	isFullscreen,
	overlayTarget,
	toggleFullscreen: requestFullscreen
} = useCanvasFullscreen(canvasHost, () => canvas.value?.zoomToFit());
const drawerVisible = ref(false),
	creating = ref(false),
	busy = ref(false),
	saveError = ref<FactError | null>(null);
const selectedIds = ref<string[]>([]),
	selectedRelation = ref(""),
	zoom = ref(100);
const linkMode = ref(false),
	linkSource = ref(""),
	linkVisible = ref(false),
	linkRelation = ref(""),
	linkError = ref<FactError | null>(null);
const linkPair = ref({ source: "", target: "", revision: 0 });
const importVisible = ref(false),
	importFile = ref<File | null>(null),
	fileInput = ref<HTMLInputElement>(),
	importError = ref(""),
	importResult = ref<FactImport | null>(null);
const protectedVisible = ref(false),
	protectedRefs = ref<FactReference[]>([]);
const historyVisible = ref(false),
	historyLoading = ref(false),
	history = ref<FactRecord[]>([]),
	historyTotal = ref(0),
	historyPage = ref(1);
const recordNames: Record<FactRecord["recordKind"], string> = {
	IMPORT: "拓扑导入",
	PURGE: "数据清除",
	EDIT: "实例编辑",
	INSTANCE_DELETE: "实例删除",
	INSTANCE_CREATE: "手工创建"
};
const linkOptions = computed(() => {
	const source = store.graph.nodes.find(value => value.instanceId === linkPair.value.source),
		target = store.graph.nodes.find(value => value.instanceId === linkPair.value.target);
	return source && target ? matchingRelations(store.catalog.relations, source, target) : [];
});
let searchTimer: ReturnType<typeof setTimeout> | undefined;
function nodeName(id: string) {
	return (
		store.graph.nodes.find(node => node.instanceId === id)?.businessIdentityText ||
		store.list.items.find(node => node.instanceId === id)?.businessIdentityText ||
		id
	);
}
async function allowedToLeave() {
	return !drawerVisible.value || !editor.value || (await editor.value.canLeave());
}
function selectType(id: string) {
	return selectTypes(
		store.filters.types.includes(id) ? store.filters.types.filter(value => value !== id) : [...store.filters.types, id]
	);
}
async function selectTypes(types: string[]) {
	if (!(await allowedToLeave())) return;
	drawerVisible.value = false;
	store.clearSelection();
	selectedIds.value = [];
	selectedRelation.value = "";
	store.focus = "";
	linkMode.value = false;
	linkSource.value = "";
	store.filters.types = types;
	await router.replace({ query: {} });
}
async function pick(id: string) {
	if (!(await allowedToLeave())) {
		selectedIds.value = store.selected ? [store.selected.instanceId] : [];
		return false;
	}
	saveError.value = null;
	creating.value = false;
	selectedRelation.value = "";
	try {
		await store.select(id);
		drawerVisible.value = true;
		selectedIds.value = [id];
		return true;
	} catch (error) {
		ElMessage.error({ message: (error as FactError).message, appendTo: overlayTarget.value });
		return false;
	}
}
async function canvasPick(id: string) {
	if (!linkMode.value) {
		await pick(id);
		return;
	}
	if (!linkSource.value) {
		linkSource.value = id;
		selectedIds.value = [id];
	} else await beginLink(linkSource.value, id);
}
async function pickEdge(id: string) {
	const edge = store.graph.edges.find(value => value.relationId === id);
	if (!edge || !(await pick(edge.sourceInstanceId))) return;
	selectedRelation.value = id;
	await nextTick();
	editor.value?.showRelations();
}
async function blankClick() {
	if (!(await allowedToLeave())) return;
	drawerVisible.value = false;
	store.clearSelection();
	selectedRelation.value = "";
	selectedIds.value = [];
}
async function startCreate() {
	if (!(await allowedToLeave())) return;
	store.clearSelection();
	creating.value = true;
	saveError.value = null;
	drawerVisible.value = true;
}
async function save(body: FactCreate | FactEdit) {
	if (!store.writable || busy.value) return;
	busy.value = true;
	saveError.value = null;
	try {
		let id = store.selected?.instanceId;
		if (creating.value) {
			const created = await factApi.create(body as FactCreate, store.role, store.revision);
			id = created.instanceId;
		} else if (id && store.selected) await factApi.edit(id, body as FactEdit, store.role, store.selected.revision);
		ElMessage.success({ message: creating.value ? "实例已创建并留痕" : "实例修改已保存并留痕", appendTo: overlayTarget.value });
		creating.value = false;
		await store.refresh();
		if (id) {
			await store.select(id);
		}
	} catch (error) {
		saveError.value = error as FactError;
	} finally {
		busy.value = false;
	}
}
async function reloadDetail() {
	if (!store.selected || !(await allowedToLeave())) return;
	await store.select(store.selected.instanceId);
	saveError.value = null;
}
async function refresh() {
	if (!(await allowedToLeave())) return;
	await store.refresh();
	if (store.selected) {
		try {
			await store.select(store.selected.instanceId);
		} catch {
			store.clearSelection();
			drawerVisible.value = false;
		}
	}
}
function moveNodes(positions: Positions) {
	store.positions = { ...store.positions, ...positions };
}
async function applyLayout(command: "horizontal" | "vertical") {
	store.layout(command);
	await nextTick();
	canvas.value?.zoomToFit();
}
async function focusInstance(id: string) {
	if (!(await allowedToLeave())) return;
	store.focus = id;
	store.filters.types = [];
	store.filters.domain = "";
	store.filters.q = "";
	store.filters.mark = "";
	await store.refresh();
	await nextTick();
	canvas.value?.centerOn(id);
}
async function clearFocus() {
	store.focus = "";
	await store.refresh();
	await nextTick();
	canvas.value?.zoomToFit();
}
async function toggleLink() {
	if (!(await allowedToLeave())) return;
	linkMode.value = !linkMode.value;
	linkSource.value = "";
	drawerVisible.value = false;
}
async function beginLink(source: string, target: string) {
	if (!store.writable || !(await allowedToLeave())) return;
	linkPair.value = { source, target, revision: store.revision };
	linkRelation.value = "";
	linkError.value = null;
	if (!linkOptions.value.length) {
		ElMessage.warning({ message: "这两个实例的对象类型之间没有可用关系，请先在对象建模中定义", appendTo: overlayTarget.value });
		linkSource.value = "";
		return;
	}
	linkRelation.value = linkOptions.value[0].id;
	linkVisible.value = true;
}
async function submitLink() {
	if (busy.value || !store.writable) return;
	busy.value = true;
	linkError.value = null;
	try {
		await factApi.edit(
			linkPair.value.source,
			{ relationOps: [{ op: "add", relationTypeRef: linkRelation.value, targetInstanceId: linkPair.value.target }] },
			store.role,
			linkPair.value.revision
		);
		linkVisible.value = false;
		linkMode.value = false;
		linkSource.value = "";
		ElMessage.success({ message: "关系已保存并留痕", appendTo: overlayTarget.value });
		await store.refresh();
		if (store.selected) await store.select(store.selected.instanceId);
	} catch (error) {
		linkError.value = error as FactError;
	} finally {
		busy.value = false;
	}
}
async function openImport() {
	if (!(await allowedToLeave())) return;
	drawerVisible.value = false;
	importFile.value = null;
	importResult.value = null;
	importError.value = "";
	importVisible.value = true;
}
function setFile(file?: File) {
	if (!file || busy.value) return;
	importFile.value = file;
	importResult.value = null;
	importError.value = file.size > 20 * 1024 * 1024 ? "文件超过 20 MB，请拆分拓扑" : "";
}
function chooseFile(event: Event) {
	setFile((event.target as HTMLInputElement).files?.[0]);
	(event.target as HTMLInputElement).value = "";
}
function dropFile(event: DragEvent) {
	setFile(event.dataTransfer?.files[0]);
}
async function submitImport() {
	if (!importFile.value || importError.value || busy.value || !store.writable) return;
	busy.value = true;
	importError.value = "";
	try {
		let document: unknown;
		try {
			document = JSON.parse((await importFile.value.text()).replace(/^\uFEFF/, ""));
		} catch {
			importError.value = "文件无法解析，请提供 UTF-8 编码的有效 JSON";
			return;
		}
		importResult.value = await factApi.import(document, store.role, store.revision);
		await store.refresh();
	} catch (error) {
		const failure = error as FactError;
		importError.value = failure.message;
		if (failure.details?.length)
			importResult.value = {
				batchId: "",
				result: "REJECTED",
				instanceCount: 0,
				relationCount: 0,
				errors: failure.details,
				revision: store.revision
			};
		await store.refresh();
	} finally {
		busy.value = false;
	}
}
async function deleteInstance() {
	if (!store.selected || !store.writable || busy.value) return;
	try {
		await ElMessageBox.confirm(`删除 ${store.selected.businessIdentityText}？稳定标识不会复用，操作将留痕。`, "删除实例", {
			appendTo: overlayTarget.value,
			type: "warning",
			confirmButtonText: "删除",
			cancelButtonText: "取消"
		});
	} catch {
		return;
	}
	busy.value = true;
	saveError.value = null;
	try {
		await factApi.delete(store.selected.instanceId, store.role, store.selected.revision);
		await afterDelete();
	} catch (error) {
		const failure = error as FactError;
		if (failure.code === "OF_DELETE_PROTECTED") {
			protectedRefs.value = [...store.selected.outbound, ...store.selected.inbound];
			protectedVisible.value = true;
		} else saveError.value = failure;
	} finally {
		busy.value = false;
	}
}
async function deleteAfterUnlink() {
	if (!store.selected || busy.value) return;
	try {
		await ElMessageBox.confirm(
			`确认解除 ${new Set(protectedRefs.value.map(edge => edge.relationId)).size} 条关联后删除实例？整个操作将一起校验和提交。`,
			"解除引用并删除",
			{ appendTo: overlayTarget.value, type: "warning", confirmButtonText: "确认解除并删除", cancelButtonText: "取消" }
		);
	} catch {
		return;
	}
	busy.value = true;
	try {
		await factApi.edit(
			store.selected.instanceId,
			{
				relationOps: [...new Set(protectedRefs.value.map(edge => edge.relationId))].map(relationId => ({
					op: "remove",
					relationId
				})),
				deleteInstance: true
			},
			store.role,
			store.selected.revision
		);
		protectedVisible.value = false;
		await afterDelete();
	} catch (error) {
		protectedVisible.value = false;
		saveError.value = error as FactError;
	} finally {
		busy.value = false;
	}
}
async function afterDelete() {
	drawerVisible.value = false;
	store.clearSelection();
	store.focus = "";
	selectedIds.value = [];
	await router.replace({ query: {} });
	await store.refresh();
	ElMessage.success({ message: "实例已删除并留痕", appendTo: overlayTarget.value });
}
async function moreAction(command: "check" | "history") {
	if (command === "history") {
		historyVisible.value = true;
		historyPage.value = 1;
		await loadHistory();
		return;
	}
	if (!(await allowedToLeave()) || !store.writable || busy.value) return;
	busy.value = true;
	try {
		if (command === "check") {
			const result = await factApi.check(store.role, store.revision);
			ElMessage.success({
				message: `已检查 ${result.checkedCount} 个实例：${result.pendingReview.length} 个待复核，${result.invalidRef.length} 个引用失效`,
				appendTo: overlayTarget.value
			});
			await store.refresh();
			if (store.selected) await store.select(store.selected.instanceId);
		}
	} catch (error) {
		ElMessage.error({ message: (error as FactError).message, appendTo: overlayTarget.value });
	} finally {
		busy.value = false;
	}
}
async function loadHistory() {
	historyLoading.value = true;
	try {
		const result = await factApi.batches(historyPage.value);
		history.value = result.items;
		historyTotal.value = result.total;
	} catch (error) {
		ElMessage.error({ message: (error as FactError).message, appendTo: overlayTarget.value });
	} finally {
		historyLoading.value = false;
	}
}
async function toggleFullscreen() {
	try {
		await requestFullscreen();
	} catch {
		ElMessage.warning({ message: "当前浏览器不支持全屏", appendTo: overlayTarget.value });
	}
}
function keyDown(event: KeyboardEvent) {
	if (event.key === "Escape") {
		linkMode.value = false;
		linkSource.value = "";
	}
}
watch(
	() => [store.filters.types.join(","), store.filters.domain, store.filters.mark],
	async () => {
		store.filters.page = 1;
		await store.refresh();
		await nextTick();
		canvas.value?.zoomToFit();
	}
);
watch(
	() => store.filters.q,
	() => {
		clearTimeout(searchTimer);
		searchTimer = setTimeout(async () => {
			store.filters.page = 1;
			await store.refresh();
			await nextTick();
			canvas.value?.zoomToFit();
		}, 250);
	}
);
watch(
	() => store.role,
	() => {
		linkMode.value = false;
		linkSource.value = "";
		drawerVisible.value = false;
		store.clearSelection();
	}
);
watch(
	() => route.query.focus,
	value => {
		if (typeof value === "string" && store.selected?.instanceId !== value) void pick(value);
	}
);
onBeforeRouteLeave(async () => await allowedToLeave());
onMounted(async () => {
	await store.refresh();
	if (typeof route.query.focus === "string") await pick(route.query.focus);
	window.addEventListener("keydown", keyDown);
});
onBeforeUnmount(() => {
	clearTimeout(searchTimer);
	window.removeEventListener("keydown", keyDown);
});
</script>

<style scoped>
:global(.ctff-app-shell:has(.object-fact-page)) {
	--ctff-bg-color-page: #f5f7fa;
	--ctff-page-background-image: #f5f7fa;
	--ctff-shell-sidebar-shadow: none;
	--ctff-shell-topbar-shadow: none;
}

:global(.ctff-app-shell:has(.object-fact-page) .ctff-shell-topbar),
:global(.ctff-app-shell:has(.object-fact-page) .ctff-shell-sidebar) {
	border-color: #e5e6eb;
	box-shadow: none;
}

:global(.ctff-app-shell:has(.object-fact-page) .ctff-workspace) {
	display: flex;
	flex-direction: column;
	padding: 16px;
	overflow: hidden;
	background: #f5f7fa;
}

.object-fact-page {
	box-sizing: border-box;
	display: flex;
	flex: 1;
	width: 100%;
	min-width: 0;
	min-height: 0;
}

.fact-shell {
	display: flex;
	flex: 1;
	flex-direction: column;
	min-width: 0;
	min-height: 0;
	overflow: hidden;
	background: #fff;
	border: 1px solid #e9ecf1;
	border-radius: 14px;
	box-shadow: 0 2px 6px rgb(15 40 80 / 2%);
}

.fact-toolbar {
	display: flex;
	flex-shrink: 0;
	flex-wrap: wrap;
	gap: 10px;
	align-items: center;
	padding: 12px 16px;
	background: #fff;
	border-bottom: 1px solid #dedfe4;
}

.fact-toolbar :deep(.el-button),
.fact-toolbar :deep(.el-dropdown) {
	flex-shrink: 0;
}

.fact-toolbar :deep(.el-button + .el-button) {
	margin-left: 0;
}

.fact-search {
	flex: 0 0 200px;
	width: 200px;
}

.fact-domain-filter,
.fact-mark-filter {
	flex: 0 0 122px;
	width: 122px;
}

.toolbar-spacer {
	flex: 1;
	min-width: 0;
}

.fact-body {
	display: flex;
	flex: 1;
	gap: 0;
	min-height: 0;
	overflow: hidden;
}

.fact-status i {
	flex-shrink: 0;
	width: 8px;
	height: 8px;
	border-radius: 50%;
}

.fact-canvas-host {
	position: relative;
	flex: 1;
	min-width: 0;
	min-height: 0;
	overflow: hidden;
	border: 0;
	border-radius: 0;
}

.fact-canvas-host:fullscreen {
	width: 100vw;
	height: 100vh;
	min-height: 0;
	background: var(--el-bg-color);
	border: 0;
	border-radius: 0;
}

.fact-canvas-overlay {
	position: absolute;
	inset: 0;
	display: grid;
	place-items: center;
	background: var(--el-bg-color);
}

.fact-canvas-overlay.is-empty {
	pointer-events: none;
	background: transparent;
}

.fact-canvas-overlay.is-empty :deep(.el-button) {
	pointer-events: auto;
}

.fact-canvas-footer {
	position: absolute;
	right: 12px;
	bottom: 12px;
	left: 12px;
	display: flex;
	flex-wrap: wrap;
	gap: 8px;
	align-items: center;
	justify-content: space-between;
	pointer-events: none;
}

.fact-status,
.fact-viewport-controls {
	display: flex;
	flex-wrap: wrap;
	gap: 8px;
	align-items: center;
	padding: 5px 9px;
	font-size: 11px;
	color: #58718b;
	pointer-events: auto;
	background: rgb(255 255 255 / 94%);
	border: 1px solid #e0e9f2;
	border-radius: 9px;
}

.fact-status button {
	display: inline-flex;
	gap: 5px;
	align-items: center;
	padding: 4px;
	font: inherit;
	color: inherit;
	cursor: pointer;
	background: transparent;
	border: 0;
	border-radius: 4px;
}

.fact-status button:hover {
	background: #eef5fb;
}

.fact-status button.is-inactive {
	opacity: 0.4;
}

.fact-viewport-controls {
	gap: 2px;
}

.fact-viewport-controls :deep(.el-button + .el-button) {
	margin-left: 0;
}

.fact-mode-banner {
	flex-shrink: 0;
	margin: 12px 16px 0;
	border-radius: 8px;
}

.fact-hops {
	width: 112px;
}

.fact-truncated {
	position: absolute;
	top: 12px;
	right: 12px;
	left: 12px;
	width: auto;
}

.fact-upload-zone {
	padding: 26px;
	color: var(--el-color-primary);
	text-align: center;
	background: var(--el-color-primary-light-9);
	border: 1px dashed var(--el-color-primary-light-5);
	border-radius: 10px;
}

.fact-help {
	font-size: 12px;
	line-height: 1.7;
	color: var(--el-text-color-secondary);
}

.fact-record-detail {
	max-height: 360px;
	padding: 12px;
	overflow: auto;
	font-size: 12px;
	overflow-wrap: anywhere;
	white-space: pre-wrap;
	background: var(--el-fill-color-lighter);
}

@media (width <= 960px) {
	.fact-toolbar {
		padding: 8px 12px;
	}

	.fact-canvas-footer {
		flex-direction: column;
		align-items: flex-start;
	}
}

@media (width <= 600px) {
	:global(.ctff-app-shell:has(.object-fact-page) .ctff-workspace) {
		padding: 8px;
	}

	.fact-body {
		flex-direction: column;
	}

	.fact-search {
		flex: 1 0 180px;
	}
}
</style>
