import { defineStore } from "pinia";
import { computed, ref } from "vue";
import type { GraphObject, GraphRelation } from "@/modules/object-model/types";
import { layeredLayout, type Positions } from "@/modules/object-model/canvas-layout";
import { factApi } from "./api";
import type { FactCatalog, FactDetail, FactError, FactGraph, FactList, FactRole } from "./types";

export const useObjectFactStore = defineStore("object-fact", () => {
	const catalog = ref<FactCatalog>({ types: [], relations: [], domains: [], revision: 0, instanceCount: 0, relationCount: 0 });
	const list = ref<FactList>({ items: [], total: 0, page: 1, pageSize: 50, revision: 0, instanceCount: 0, relationCount: 0 });
	const graph = ref<FactGraph>({ nodes: [], edges: [], total: 0, truncated: false, revision: 0, instanceCount: 0, relationCount: 0 });
	const selected = ref<FactDetail | null>(null);
	const loading = ref(false);
	const detailLoading = ref(false);
	const error = ref<FactError | null>(null);
	const role = ref<FactRole>("MODELER");
	const filters = ref({ type: "", domain: "", q: "", mark: "", page: 1 });
	const focus = ref("");
	const hops = ref(1);
	const positions = ref<Positions>({});
	let generation = 0, selectionGeneration = 0;
	const revision = computed(() => Math.max(catalog.value.revision, list.value.revision, graph.value.revision));
	const writable = computed(() => role.value === "MODELER");
	const colors = computed(() => new Map(catalog.value.domains.map((domain, index) => [domain.domainId, domain.color ?? ["#38A6F2", "#14b8a6", "#8b7ee8", "#e5a149"][index % 4]])));
	const domains = computed(() => new Map(catalog.value.domains.map(domain => [domain.domainId, domain.name])));
	const objects = computed<GraphObject[]>(() => graph.value.nodes.map((node, index) => ({
		id: node.instanceId, name: node.displayName, displayCode: `${node.businessIdentityText} · ${node.typeName}`,
		markLabel: node.marks.some(mark => mark.markKind === "invalidRef") ? "引用失效" : node.marks.length ? "待复核" : "",
		domainId: node.domainId, identityAttrNames: Object.keys(node.businessIdentity), completeness: "complete", status: "active", revision: 1,
		layout: positions.value[node.instanceId] ?? { x: 72 + index % 4 * 370, y: 64 + Math.floor(index / 4) * 196 }
	})));
	const relations = computed<GraphRelation[]>(() => graph.value.edges.map(edge => {
		const definition = catalog.value.relations.find(value => value.id === edge.relationTypeRef);
		return { id: edge.relationId, name: edge.relationName, sourceType: edge.sourceInstanceId, targetType: edge.targetInstanceId,
			sourceCardinality: { min: 0, max: 1 }, targetCardinality: { min: 0, max: 1 }, compositionKind: definition?.compositionKind ?? "Association",
			annotation: { label: edge.relationName, showLabel: true }, status: "active", revision: 1 };
	}));
	const highlightedIds = computed(() => {
		const q = filters.value.q.trim().toLowerCase();
		return new Set(graph.value.nodes.filter(node => q && `${node.displayName} ${node.businessIdentityText} ${node.instanceId}`.toLowerCase().includes(q)).map(node => node.instanceId));
	});
	function layout(direction: "horizontal" | "vertical") { positions.value = { ...positions.value, ...layeredLayout(objects.value, relations.value, direction) }; }
	async function refresh() {
		const token = ++generation;
		loading.value = true; error.value = null;
		try {
			const [nextCatalog, nextList, nextGraph] = await Promise.all([
				factApi.catalog(), factApi.list({ ...filters.value, pageSize: 50 }),
				factApi.graph({ types: filters.value.type, domains: filters.value.domain, focus: focus.value, hops: hops.value })
			]);
			if (token !== generation) return;
			catalog.value = nextCatalog; list.value = nextList; graph.value = nextGraph;
			if (nextGraph.nodes.some(node => !positions.value[node.instanceId])) layout("horizontal");
		} catch (caught) { if (token === generation) error.value = caught as FactError; }
		finally { if (token === generation) loading.value = false; }
	}
	async function select(id: string) {
		const token = ++selectionGeneration;
		detailLoading.value = true;
		try {
			const detail = await factApi.detail(id);
			if (token !== selectionGeneration) return;
			selected.value = detail;
			if (!graph.value.nodes.some(node => node.instanceId === id)) {
				focus.value = id; filters.value.type = ""; filters.value.domain = "";
				await refresh();
			}
		} finally { if (token === selectionGeneration) detailLoading.value = false; }
	}
	function clearSelection() { selectionGeneration++; selected.value = null; detailLoading.value = false; }
	return { catalog, list, graph, selected, loading, detailLoading, error, role, writable, filters, focus, hops, positions, revision, colors, domains, objects, relations, highlightedIds, refresh, select, clearSelection, layout };
});
