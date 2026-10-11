import { ElMessage } from "element-plus";
import { defineStore } from "pinia";

import { objectModelApi } from "./api";
import { changedPositions, type LayoutCommand, type Positions } from "./canvas-layout";
import type { ApiError, DomainDef, GraphData, ObjectTypeDetail, RelationDetail } from "./types";

// 迭代五清新色系兜底调色板：天蓝 / 薄荷青 / 活力杏橙 / 珊瑚 / 紫罗兰 / 琥珀（轮转分配）
const DOMAIN_PALETTE = ["#38A6F2", "#14C8B4", "#FF9E5E", "#E6538E", "#A855F7", "#E8B931"];
const LEGACY_BLUE_COLORS = new Set(["#2F80C9", "#4D9DE0", "#61C8FF", "#4F7DF3", "#635BFF", "#0EA5A0", "#F97316"]);

function domainUiColor(domain: DomainDef, index: number) {
	const raw = domain.color?.trim().toUpperCase();
	if (!raw || raw.startsWith("VAR(") || LEGACY_BLUE_COLORS.has(raw)) {
		return DOMAIN_PALETTE[index % DOMAIN_PALETTE.length];
	}
	return domain.color as string;
}

export type DrawerMode = "object" | "create-object" | "relation" | "create-relation";

interface ObjectModelState {
	layoutRevision: number;
	layoutPendingSaves: number;
	layoutUndoStack: LayoutCommand[];
	layoutRedoStack: LayoutCommand[];
	domains: DomainDef[];
	objects: GraphData["objects"];
	relations: GraphData["relations"];
	loading: boolean;
	loadError: string;
	saveError: ApiError | null;
	saving: boolean;
	domainFilter: string | null;
	searchText: string;
	drawerVisible: boolean;
	drawerMode: DrawerMode;
	currentObject: ObjectTypeDetail | null;
	currentRelation: RelationDetail | null;
	pendingRelationSource: string | null;
	pendingRelationTarget: string | null;
}

const layoutQueues = new WeakMap<object, Promise<void>>();
function queueLayout(owner: { layoutPendingSaves: number }, operation: () => Promise<boolean>) {
	owner.layoutPendingSaves++;
	const next = (layoutQueues.get(owner) ?? Promise.resolve()).then(operation);
	layoutQueues.set(owner, next.then(() => undefined, () => undefined));
	return next.finally(() => { owner.layoutPendingSaves--; });
}

function layoutSignature(objects: GraphData["objects"]) {
	return JSON.stringify(objects.map(object => [object.id, object.layout.x, object.layout.y]).sort((a, b) => String(a[0]).localeCompare(String(b[0]))));
}

function showApiError(error: unknown, fallback: string) {
	const apiError = error as ApiError;
	if (apiError?.code) {
		const detail = apiError.details?.length ? `（${apiError.details.map(d => d.field).join("、")}）` : "";
		ElMessage.error(`${apiError.message}${detail}`);
	}
	else {
		ElMessage.error(fallback);
	}
}

export const useObjectModelStore = defineStore("objectModel", {
	state: (): ObjectModelState => ({
		layoutRevision: 1,
		layoutPendingSaves: 0,
		layoutUndoStack: [],
		layoutRedoStack: [],
		domains: [],
		objects: [],
		relations: [],
		loading: false,
		loadError: "",
		saveError: null,
		saving: false,
		domainFilter: null,
		searchText: "",
		drawerVisible: false,
		drawerMode: "object",
		currentObject: null,
		currentRelation: null,
		pendingRelationSource: null,
		pendingRelationTarget: null
	}),

	getters: {
		layoutBusy: state => state.layoutPendingSaves > 0,
		domainColorMap(state): Map<string, string> {
			return new Map(state.domains.map((domain, index) => [domain.domainId, domainUiColor(domain, index)]));
		},
		domainNameMap(state): Map<string, string> {
			return new Map(state.domains.map(domain => [domain.domainId, domain.name]));
		},
		visibleObjects(state): GraphData["objects"] {
			const seen = new Set<string>();
			let list = state.objects.filter(object => {
				if (object.status === "deprecated" || seen.has(object.id)) {
					return false;
				}
				seen.add(object.id);
				return true;
			});
			if (state.domainFilter) {
				list = list.filter(object => object.domainId === state.domainFilter);
			}
			return list;
		},
		visibleRelations(state): GraphData["relations"] {
			const visible = new Set(this.visibleObjects.map(object => object.id));
			return state.relations.filter(relation => {
				if (relation.status === "deprecated") return false;
				const targets = Array.isArray(relation.targetType) ? relation.targetType : [relation.targetType];
				return visible.has(relation.sourceType) && targets.some(target => visible.has(target));
			});
		},
		highlightedIds(state): Set<string> {
			const keyword = state.searchText.trim().toLowerCase();
			if (!keyword) {
				return new Set();
			}
			const visibleIds = new Set(
				state.objects
					.filter(object => object.status !== "deprecated" && (!state.domainFilter || object.domainId === state.domainFilter))
					.map(object => object.id)
			);
			return new Set(
				state.objects
					.filter(
						object =>
							visibleIds.has(object.id) &&
							object.status !== "deprecated" &&
							(
								object.name.toLowerCase().includes(keyword) ||
								object.id.toLowerCase().includes(keyword) ||
								object.identityAttrNames.some(name => name.toLowerCase().includes(keyword))
							)
					)
					.map(object => object.id)
			);
		}
	},

	actions: {
		async loadGraph() {
			this.loading = true;
			this.loadError = "";
			try {
				const data = await objectModelApi.fetchGraph();
				if (layoutSignature(this.objects) !== layoutSignature(data.objects)) {
					this.layoutUndoStack = [];
					this.layoutRedoStack = [];
				}
				this.layoutRevision = data.layoutRevision;
				this.domains = data.domains;
				this.objects = data.objects;
				this.relations = data.relations;
			}
			catch (error) {
				this.loadError = (error as ApiError).message || "画布加载失败";
				showApiError(error, "画布加载失败");
			}
			finally {
				this.loading = false;
			}
		},

		async openObject(id: string) {
			try {
				this.currentObject = await objectModelApi.fetchObjectType(id);
				this.currentRelation = null;
				this.saveError = null;
				this.drawerMode = "object";
				this.drawerVisible = true;
			}
			catch (error) {
				showApiError(error, "对象加载失败");
			}
		},

		startCreateObject() {
			this.currentObject = null;
			this.currentRelation = null;
			this.saveError = null;
			this.drawerMode = "create-object";
			this.drawerVisible = true;
		},

		async openRelation(id: string) {
			try {
				this.currentRelation = await objectModelApi.fetchRelation(id);
				this.currentObject = null;
				this.drawerMode = "relation";
				this.drawerVisible = true;
				this.saveError = null;
			}
			catch (error) { showApiError(error, "关系详情加载失败"); }
		},

		startCreateRelation(sourceType: string, targetType: string) {
			this.pendingRelationSource = sourceType;
			this.pendingRelationTarget = targetType;
			this.currentObject = null;
			this.currentRelation = null;
			this.saveError = null;
			this.drawerMode = "create-relation";
			this.drawerVisible = true;
		},

		closeDrawer() {
			this.drawerVisible = false;
			this.currentObject = null;
			this.currentRelation = null;
			this.pendingRelationSource = null;
			this.pendingRelationTarget = null;
		},

		async saveObject(draft: Partial<ObjectTypeDetail>) {
			this.saving = true;
			this.saveError = null;
			try {
				if (this.drawerMode === "create-object") {
					const created = await objectModelApi.createObjectType(draft);
					ElMessage.success(`对象 ${created.name} 已创建（修订 1）`);
					this.currentObject = created;
					this.drawerMode = "object";
				}
				else if (this.currentObject) {
					const updated = await objectModelApi.updateObjectType(
						this.currentObject.id,
						draft,
						this.currentObject.revision
					);
					ElMessage.success(`已保存（修订 ${updated.revision}）`);
					this.currentObject = updated;
				}
				await this.loadGraph();
			}
			catch (error) {
				this.saveError = error as ApiError;
				showApiError(error, "保存失败");
				throw error;
			}
			finally {
				this.saving = false;
			}
		},

		async deprecateCurrentObject() {
			if (!this.currentObject) {
				return;
			}
			this.saving = true;
			try {
				await objectModelApi.deprecateObjectType(this.currentObject.id, this.currentObject.revision);
				ElMessage.success("对象已废弃");
				this.closeDrawer();
				await this.loadGraph();
			}
			catch (error) {
				showApiError(error, "废弃请求失败");
			}
			finally {
				this.saving = false;
			}
		},

		async saveRelation(draft: Partial<RelationDetail>) {
			this.saving = true;
			this.saveError = null;
			try {
				if (this.drawerMode === "create-relation") {
					const created = await objectModelApi.createRelation(draft);
					ElMessage.success(`关系 ${created.name} 已创建`);
					this.currentRelation = created;
					this.drawerMode = "relation";
				}
				else if (this.currentRelation) {
					const updated = await objectModelApi.updateRelation(
						this.currentRelation.id,
						draft,
						this.currentRelation.revision
					);
					ElMessage.success(`关系已保存（修订 ${updated.revision}）`);
					this.currentRelation = updated;
				}
				await this.loadGraph();
			}
			catch (error) {
				this.saveError = error as ApiError;
				showApiError(error, "关系保存失败");
				throw error;
			}
			finally {
				this.saving = false;
			}
		},

		async deleteCurrentRelation() {
			if (!this.currentRelation) {
				return;
			}
			this.saving = true;
			try {
				await objectModelApi.deleteRelation(this.currentRelation.id);
				ElMessage.success("关系已删除");
				this.closeDrawer();
				await this.loadGraph();
			}
			catch (error) {
				showApiError(error, "关系删除失败");
			}
			finally {
				this.saving = false;
			}
		},

		async refreshCurrentObject() {
			if (this.currentObject) {
				this.currentObject = await objectModelApi.fetchObjectType(this.currentObject.id);
			}
		},

		saveNodePositions(positions: Positions): Promise<boolean> {
			return queueLayout(this, async () => {
				const before = Object.fromEntries(this.objects.map(object => [object.id, { ...object.layout }]));
				const command = changedPositions(before, positions);
				if (!Object.keys(command.after).length) return true;
				if (!await this.persistNodePositions(command.after)) return false;
				this.layoutUndoStack.push(command);
				if (this.layoutUndoStack.length > 50) this.layoutUndoStack.shift();
				this.layoutRedoStack = [];
				return true;
			});
		},

		undoLayout(): Promise<boolean> {
			return queueLayout(this, async () => {
				const command = this.layoutUndoStack.at(-1);
				if (!command || !await this.persistNodePositions(command.before)) return false;
				this.layoutUndoStack.pop();
				this.layoutRedoStack.push(command);
				return true;
			});
		},

		redoLayout(): Promise<boolean> {
			return queueLayout(this, async () => {
				const command = this.layoutRedoStack.at(-1);
				if (!command || !await this.persistNodePositions(command.after)) return false;
				this.layoutRedoStack.pop();
				this.layoutUndoStack.push(command);
				return true;
			});
		},

		async persistNodePositions(positions: Positions): Promise<boolean> {
			const before: Positions = {};
			for (const object of this.objects) {
				if (!positions[object.id]) continue;
				before[object.id] = { ...object.layout };
				object.layout = { ...positions[object.id] };
			}
			try {
				const result = await objectModelApi.saveLayout(this.layoutRevision, positions);
				this.layoutRevision = result.layoutRevision;
				return true;
			}
			catch (error) {
				for (const object of this.objects) {
					if (before[object.id]) object.layout = before[object.id];
				}
				// 布局 409 静默重载（TDD-OM-002 §12.6：纯 UI 数据，不打扰用户）
				const apiError = error as ApiError;
				if (apiError?.code === "REVISION_CONFLICT") {
					this.layoutUndoStack = [];
					this.layoutRedoStack = [];
					await this.loadGraph();
				}
				else showApiError(error, "布局保存失败");
				return false;
			}
		}
	}
});
