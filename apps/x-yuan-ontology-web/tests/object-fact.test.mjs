import assert from "node:assert/strict";
import { after, before, test } from "node:test";
import { createRenderer, h, markRaw, nextTick, reactive, ref, ssrContextKey } from "vue";
import { createPinia, setActivePinia } from "pinia";
import { createServer } from "vite";
import { renderToString } from "vue/server-renderer";

let server, Drawer, TypeList, Canvas, form, api, useStore, fullscreen, graphs, presentation;
const renderer = createRenderer({
	insert() {},
	remove() {},
	patchProp() {},
	setText() {},
	setElementText() {},
	createElement: () => ({}),
	createText: () => ({}),
	createComment: () => ({}),
	parentNode: () => null,
	nextSibling: () => null
});
const attributes = [
	{
		id: "CODE",
		name: "code",
		dataType: "String",
		required: true,
		nullable: false,
		readOnlyNature: "创建后不可变",
		cardinality: { min: 1, max: 1 }
	},
	{ id: "NAME", name: "name", dataType: "String", required: true, nullable: false, cardinality: { min: 1, max: 1 } },
	{
		id: "FLAG",
		name: "flag",
		dataType: "Boolean",
		required: false,
		nullable: true,
		nullMeaning: "UNKNOWN",
		cardinality: { min: 0, max: 1 }
	},
	{ id: "LABELS", name: "labels", dataType: "String", required: false, cardinality: { min: 0, max: "N" } }
];
const objectType = {
	id: "OBJ-SVC",
	name: "服务",
	domainId: "OPS",
	identitySpec: { attributes: ["CODE"] },
	attributes,
	status: "active"
};
const catalog = {
	types: [objectType],
	relations: [],
	domains: [{ domainId: "OPS", name: "运维" }],
	revision: 1,
	instanceCount: 2,
	relationCount: 1
};
const detail = {
	instanceId: "INST-000001",
	objectTypeRef: "OBJ-SVC",
	displayName: "服务一",
	businessIdentity: { code: "S1" },
	businessIdentityText: "S1",
	sourceRef: "MANUAL",
	domainId: "OPS",
	propertyValues: { code: "S1", name: "服务一", flag: { value: null, nullReason: "UNKNOWN" } },
	referenceValues: {},
	marks: [],
	outbound: [{ relationId: "RELI-000001", relationTypeRef: "REL-SUB", other: { instanceId: "INST-000002" } }],
	inbound: [],
	revision: 7
};
before(async () => {
	server = await createServer({
		server: { middlewareMode: true, hmr: false },
		appType: "custom",
		ssr: { noExternal: ["@antv/x6"] },
		plugins: [
			{
				name: "test-x6-sizing",
				enforce: "pre",
				resolveId: id => (id === "@antv/x6" ? "\0test-x6" : undefined),
				load: id =>
					id === "\0test-x6"
						? `
				export const graphs = [];
				export class Graph {
					static registerNode() {}
					static registerFilter() {}
					constructor({ container }) { this.container = container; this.sizes = []; this.events = {}; graphs.push(this); }
					on(name, callback) { this.events[name] = callback; }
					fromJSON(json) { this.json = json; }
					getCells() { return this.json.cells; }
					getCellsBBox() { return null; }
					getCellById() { return null; }
					getNodes() { return this.testNodes ?? []; }
					getEdges() { return []; }
					resize(width, height) {
						this.container.style.width = width + 'px';
						this.container.style.height = height + 'px';
						this.sizes.push([width, height]);
					}
					dispose() { this.disposed = true; }
				}
			`
						: undefined
			}
		]
	});
	Drawer = (await server.ssrLoadModule("/src/modules/object-fact/components/FactEditorDrawer.vue")).default;
	TypeList = (await server.ssrLoadModule("/src/modules/object-fact/components/FactTypeList.vue")).default;
	form = await server.ssrLoadModule("/src/modules/object-fact/form.ts");
	api = (await server.ssrLoadModule("/src/modules/object-fact/api.ts")).factApi;
	useStore = (await server.ssrLoadModule("/src/modules/object-fact/store.ts")).useObjectFactStore;
	Canvas = (await server.ssrLoadModule("/src/modules/object-model/components/ObjectCanvas.vue")).default;
	graphs = (await server.ssrLoadModule("@antv/x6")).graphs;
	fullscreen = await server.ssrLoadModule("/src/modules/object-fact/fullscreen.ts");
	presentation = await server.ssrLoadModule("/src/modules/object-fact/presentation.ts");
});
after(async () => {
	await server?.close();
});

function mountDrawer(t, overrides = {}) {
	const props = reactive({
		modelValue: false,
		creating: false,
		detail: structuredClone(detail),
		catalog: structuredClone(catalog),
		writable: true,
		saving: false,
		loading: false,
		error: null,
		...overrides
	});
	const events = [];
	let state;
	const app = renderer.createApp({
		setup() {
			state = Drawer.setup(props, { emit: (name, value) => events.push({ name, value }), expose() {} });
			return () => h("div");
		}
	});
	app.provide(ssrContextKey, { modules: new Set() });
	app.mount({});
	t.after(() => app.unmount());
	return {
		props,
		events,
		get state() {
			return state;
		}
	};
}
async function open(drawer) {
	drawer.props.modelValue = true;
	await nextTick();
	await nextTick();
}

test("opening a reactive instance preserves null reasons and does not dirty or mutate it", async t => {
	const drawer = mountDrawer(t);
	await open(drawer);
	assert.equal(drawer.state.dirty.value, false);
	assert.equal(drawer.state.editing.value, false);
	assert.deepEqual(drawer.state.fields.value.flag, { value: null, nullReason: "UNKNOWN" });
	drawer.state.fields.value.name = "changed";
	assert.equal(drawer.props.detail.propertyValues.name, "服务一");
});
test("whole-card save emits property differences and relation removals/additions together", async t => {
	const drawer = mountDrawer(t);
	await open(drawer);
	const state = drawer.state;
	state.editing.value = true;
	state.fields.value.name = "新名称";
	state.toggleRemove("RELI-000001");
	state.additions.value.push({ op: "add", relationTypeRef: "REL-SUB", targetInstanceId: "INST-000003" });
	state.save();
	const saved = drawer.events.find(event => event.name === "save").value;
	assert.deepEqual(saved.propertyValues, { name: "新名称" });
	assert.equal(saved.relationOps.length, 2);
	assert.equal(saved.relationOps[0].op, "remove");
	assert.equal(saved.relationOps[1].op, "add");
	assert.equal(drawer.props.detail.outbound.length, 1);
});
test("restoring a staged relation removes it from the outgoing change set", async t => {
	const drawer = mountDrawer(t);
	await open(drawer);
	drawer.state.toggleRemove("RELI-000001");
	assert.equal(drawer.state.dirty.value, true);
	drawer.state.toggleRemove("RELI-000001");
	assert.equal(drawer.state.dirty.value, false);
});
test("creation permits initial read-only values and emits only facts for the selected type", async t => {
	const drawer = mountDrawer(t, { creating: true, detail: null });
	await open(drawer);
	drawer.state.fields.value.code = "S2";
	drawer.state.fields.value.name = "第二个服务";
	drawer.state.save();
	assert.deepEqual(drawer.events.find(event => event.name === "save").value, {
		objectTypeRef: "OBJ-SVC",
		propertyValues: { code: "S2", name: "第二个服务" }
	});
});
test("viewer mode and in-flight saves cannot emit a mutation", async t => {
	const drawer = mountDrawer(t, { writable: false });
	await open(drawer);
	drawer.state.fields.value.name = "changed";
	drawer.state.save();
	assert.equal(drawer.events.length, 0);
	drawer.props.writable = true;
	drawer.props.saving = true;
	drawer.state.save();
	assert.equal(drawer.events.length, 0);
});
test("multi-value properties parse arrays, retain false/zero and reject invalid JSON", () => {
	const type = {
		...objectType,
		attributes: [...attributes, { id: "COUNT", name: "count", dataType: "Decimal", cardinality: { min: 0, max: 1 } }]
	};
	assert.deepEqual(form.propertyValues(type, { code: "S1", name: "one", flag: false, labels: '["A","B"]', count: 0 }), {
		code: "S1",
		name: "one",
		flag: false,
		labels: ["A", "B"],
		count: 0
	});
	assert.throws(() => form.propertyValues(type, { labels: "not-json" }), /JSON/);
	assert.throws(() => form.propertyValues(type, { labels: '{"a":1}' }), /数组/);
});
test("relation selection honors direction, multi-target types, and deprecation", () => {
	const relations = [
		{ id: "A", sourceType: "ALERT", targetType: ["SERVICE", "ASSET"], status: "active" },
		{ id: "B", sourceType: "SERVICE", targetType: "ALERT", status: "active" },
		{ id: "C", sourceType: "ALERT", targetType: "SERVICE", status: "deprecated" }
	];
	assert.deepEqual(
		form.matchingRelations(relations, { objectTypeRef: "ALERT" }, { objectTypeRef: "SERVICE" }).map(value => value.id),
		["A"]
	);
});
test("missing optional values clear according to the model's null reason", () => {
	assert.deepEqual(form.changedValues({ flag: true }, {}, objectType), { flag: { value: null, nullReason: "UNKNOWN" } });
	assert.equal(form.displayValue({ value: null, nullReason: "NOT_PROVIDED" }), "未提供");
});

test("obsolete properties are kept until explicitly removed and never become accidental null writes", async t => {
	const drawer = mountDrawer(t, {
		detail: { ...structuredClone(detail), propertyValues: { ...detail.propertyValues, oldField: "old value" } }
	});
	await open(drawer);
	drawer.state.fields.value.name = "updated";
	drawer.state.save();
	assert.deepEqual(drawer.events[0].value.propertyValues, { name: "updated" });
	assert.deepEqual(drawer.events[0].value.unsetProperties, []);
	drawer.state.togglePropertyRemoval("oldField");
	drawer.state.save();
	assert.deepEqual(drawer.events[1].value.unsetProperties, ["oldField"]);
});

test("multi-value controls convert number and boolean tags to typed values", () => {
	const type = {
		...objectType,
		attributes: [
			{ id: "NUM", name: "numbers", dataType: "Decimal", cardinality: { min: 0, max: "N" } },
			{ id: "BOOL", name: "flags", dataType: "Boolean", cardinality: { min: 0, max: "N" } }
		]
	};
	assert.deepEqual(form.propertyValues(type, { numbers: ["0", "1.5"], flags: ["true", "false"] }), {
		numbers: [0, 1.5],
		flags: [true, false]
	});
	assert.throws(() => form.propertyValues(type, { numbers: ["bad"] }), /数值/);
});
test("the instance canvas keeps separate relation IDs and uses business identity labels", async () => {
	setActivePinia(createPinia());
	const store = useStore();
	store.catalog = structuredClone(catalog);
	store.graph.nodes = [structuredClone(detail), { ...structuredClone(detail), instanceId: "INST-000002" }];
	store.graph.edges = [
		{
			relationId: "RELI-1",
			relationTypeRef: "R1",
			relationName: "service",
			sourceInstanceId: "INST-000001",
			targetInstanceId: "INST-000002"
		},
		{
			relationId: "RELI-2",
			relationTypeRef: "R2",
			relationName: "pointsTo",
			sourceInstanceId: "INST-000001",
			targetInstanceId: "INST-000002"
		}
	];
	assert.equal(store.relations.length, 2);
	assert.notEqual(store.relations[0].id, store.relations[1].id);
	assert.equal(store.relations[0].showCardinality, false);
	assert.match(store.objects[0].displayCode, /S1/);
	store.role = "VIEWER";
	assert.equal(store.writable, false);
});
test("a slower detail response cannot replace the more recently selected instance", async () => {
	setActivePinia(createPinia());
	const store = useStore();
	const original = api.detail;
	store.graph.nodes = [
		{ ...detail, instanceId: "A" },
		{ ...detail, instanceId: "B" }
	];
	let resolveA;
	api.detail = id =>
		id === "A"
			? new Promise(resolve => {
					resolveA = resolve;
				})
			: Promise.resolve({ ...detail, instanceId: "B" });
	try {
		const first = store.select("A");
		await store.select("B");
		resolveA({ ...detail, instanceId: "A" });
		await first;
		assert.equal(store.selected.instanceId, "B");
	} finally {
		api.detail = original;
	}
});

test("X6 follows available viewport size after entering and leaving fullscreen despite its own fixed pixel styles", t => {
	let observer,
		observed,
		disconnected = false;
	const previousObserver = Object.getOwnPropertyDescriptor(globalThis, "ResizeObserver");
	Object.defineProperty(globalThis, "ResizeObserver", {
		configurable: true,
		value: class {
			constructor(callback) {
				observer = callback;
			}
			observe(element) {
				observed = element;
			}
			disconnect() {
				disconnected = true;
			}
		}
	});
	t.after(() => {
		if (previousObserver) Object.defineProperty(globalThis, "ResizeObserver", previousObserver);
		else delete globalThis.ResizeObserver;
	});
	let size = { width: 960, height: 640 };
	const viewport = markRaw({ getBoundingClientRect: () => size });
	const container = markRaw({
		style: {},
		getBoundingClientRect() {
			return { width: Number.parseFloat(this.style.width), height: Number.parseFloat(this.style.height) };
		}
	});
	const app = renderer.createApp({
		setup() {
			const state = Canvas.setup(
				{ objects: [], relations: [], domainColorMap: new Map(), domainNameMap: new Map(), highlightedIds: new Set() },
				{ emit() {}, expose() {} }
			);
			state.viewportRef.value = viewport;
			state.containerRef.value = container;
			return () => h("div");
		}
	});
	app.provide(ssrContextKey, { modules: new Set() });
	app.mount({});
	assert.equal(observed, viewport);
	const graph = graphs.at(-1);
	observer();
	assert.deepEqual(graph.sizes.at(-1), [960, 640]);
	size = { width: 2560, height: 1600 };
	observer();
	assert.deepEqual(graph.sizes.at(-1), [2560, 1600]);
	size = { width: 960, height: 640 };
	observer();
	assert.deepEqual(graph.sizes.at(-1), [960, 640]);
	size = { width: 0, height: 0 };
	observer();
	assert.equal(graph.sizes.length, 3);
	app.unmount();
	assert.equal(disconnected, true);
	assert.equal(graph.disposed, true);
});

function fullscreenEnvironment(t) {
	const previous = new Map();
	const frames = new Map();
	let frameId = 0;
	const document = new EventTarget();
	document.fullscreenElement = null;
	document.exitFullscreen = async () => {
		document.fullscreenElement = null;
		document.dispatchEvent(new Event("fullscreenchange"));
	};
	for (const [key, value] of Object.entries({
		document,
		requestAnimationFrame: callback => {
			frames.set(++frameId, callback);
			return frameId;
		},
		cancelAnimationFrame: id => frames.delete(id)
	})) {
		previous.set(key, Object.getOwnPropertyDescriptor(globalThis, key));
		Object.defineProperty(globalThis, key, { configurable: true, writable: true, value });
	}
	t.after(() => {
		for (const [key, descriptor] of previous) {
			if (descriptor) Object.defineProperty(globalThis, key, descriptor);
			else delete globalThis[key];
		}
	});
	const host = markRaw({
		requestFullscreen: async () => {
			document.fullscreenElement = host;
			document.dispatchEvent(new Event("fullscreenchange"));
		}
	});
	return {
		document,
		host,
		frames,
		async flush() {
			await nextTick();
			for (const [id, callback] of frames) {
				frames.delete(id);
				callback();
			}
		}
	};
}

test("fullscreen buttons and Esc synchronize overlay targets and refit after layout settles", async t => {
	const env = fullscreenEnvironment(t);
	let state,
		childTarget,
		fits = 0;
	const app = renderer.createApp({
		setup() {
			state = fullscreen.useCanvasFullscreen(ref(env.host), () => fits++);
			return () =>
				h({
					setup() {
						childTarget = fullscreen.useFactOverlay();
						return () => h("div");
					}
				});
		}
	});
	app.mount({});
	assert.equal(childTarget.value, undefined);
	await state.toggleFullscreen();
	await env.flush();
	assert.equal(state.isFullscreen.value, true);
	assert.equal(childTarget.value, env.host);
	assert.equal(fits, 1);
	await state.toggleFullscreen();
	await env.flush();
	assert.equal(state.isFullscreen.value, false);
	assert.equal(childTarget.value, undefined);
	await state.toggleFullscreen();
	await env.flush();
	await env.document.exitFullscreen(); // Browser Esc exits without invoking the button.
	await env.flush();
	assert.equal(state.isFullscreen.value, false);
	assert.equal(childTarget.value, undefined);
	assert.equal(fits, 4);
	app.unmount();
	assert.equal(env.frames.size, 0);
});

test("failed fullscreen requests preserve normal overlays and unmount cancels pending resize work", async t => {
	const env = fullscreenEnvironment(t);
	let state,
		fits = 0;
	const app = renderer.createApp({
		setup() {
			state = fullscreen.useCanvasFullscreen(ref(env.host), () => fits++);
			return () => h("div");
		}
	});
	app.mount({});
	const request = env.host.requestFullscreen;
	env.host.requestFullscreen = async () => {
		throw new Error("fullscreen denied");
	};
	await assert.rejects(state.toggleFullscreen(), /denied/);
	assert.equal(state.isFullscreen.value, false);
	assert.equal(state.overlayTarget.value, undefined);
	env.host.requestFullscreen = request;
	await state.toggleFullscreen();
	app.unmount();
	await env.flush();
	assert.equal(env.document.fullscreenElement, null);
	assert.equal(env.frames.size, 0);
	assert.equal(fits, 0);
});

test("types in one domain retain distinct colours and full counts when the graph is filtered or the catalog reordered", () => {
	setActivePinia(createPinia());
	const store = useStore();
	store.catalog = {
		...structuredClone(catalog),
		instanceCount: 6,
		types: [
			{ ...structuredClone(objectType), id: "OBJ-SERVICE", name: "服务", instanceCount: 3 },
			{ ...structuredClone(objectType), id: "OBJ-ALERT", name: "告警", instanceCount: 3 }
		]
	};
	store.graph.nodes = [
		{ ...structuredClone(detail), objectTypeRef: "OBJ-SERVICE", typeName: "服务" },
		{ ...structuredClone(detail), instanceId: "INST-2", objectTypeRef: "OBJ-ALERT", typeName: "告警" }
	];
	const original = new Map(store.typeItems.map(type => [type.id, type.color]));
	assert.notEqual(original.get("OBJ-SERVICE"), original.get("OBJ-ALERT"));
	for (const node of store.objects) {
		const fact = store.graph.nodes.find(item => item.instanceId === node.id);
		assert.equal(node.color, original.get(fact.objectTypeRef));
		assert.equal(node.badgeLabel, fact.typeName);
	}
	store.filters.types = ["OBJ-ALERT"];
	store.graph.nodes = store.graph.nodes.filter(node => node.objectTypeRef === "OBJ-ALERT");
	store.catalog.types.reverse();
	assert.equal(store.typeItems.length, 2);
	assert.ok(store.typeItems.every(type => type.instanceCount === 3 && type.color === original.get(type.id)));
	const custom = presentation.factTypeColor("custom-type-42");
	assert.equal(presentation.factTypeColor("custom-type-42"), custom);
	store.graph.nodes.push({ ...structuredClone(detail), objectTypeRef: "REMOVED-TYPE", typeName: "已移除类型" });
	assert.ok(store.typeItems.some(type => type.id === "REMOVED-TYPE" && type.instanceCount === 1));
});

test("the sidebar supports independent type selections and deselection without changing the other selected types", async t => {
	const props = reactive({
		types: [
			{ id: "OBJ-ALERT", name: "告警", color: "#FF7B72", instanceCount: 120 },
			{ id: "OBJ-SERVICE", name: "服务", color: "#3D8BF7", instanceCount: 80 }
		],
		selectedTypes: [],
		visibleCount: 200,
		totalCount: 200
	});
	const html = await renderToString(h(TypeList, props));
	assert.match(html, /告警/);
	assert.match(html, /服务/);
	assert.match(html, /200 \/ 200/);
	assert.equal((html.match(/aria-pressed="false"/g) ?? []).length, 2);
	assert.doesNotMatch(html, /type="checkbox"/);
	assert.match(html, /120/);
	assert.match(html, /80/);
	assert.doesNotMatch(html, /INST-|SVC-|ALT-/);
	const events = [];
	let state;
	const app = renderer.createApp({
		setup() {
			state = TypeList.setup(props, { emit: (name, ids) => events.push({ name, ids }), expose() {} });
			return () => h("div");
		}
	});
	app.provide(ssrContextKey, { modules: new Set() });
	app.mount({});
	t.after(() => app.unmount());
	state.choose("OBJ-ALERT");
	assert.deepEqual(props.selectedTypes, []);
	props.selectedTypes = events.at(-1).ids;
	state.choose("OBJ-SERVICE");
	assert.deepEqual(props.selectedTypes, ["OBJ-ALERT"]);
	props.selectedTypes = events.at(-1).ids;
	const selectedHtml = await renderToString(h(TypeList, props));
	assert.equal((selectedHtml.match(/aria-pressed="true"/g) ?? []).length, 2);
	assert.match(selectedHtml, /清除筛选/);
	state.choose("OBJ-ALERT");
	props.selectedTypes = events.at(-1).ids;
	state.choose("OBJ-SERVICE");
	props.selectedTypes = events.at(-1).ids;
	assert.deepEqual(events, [
		{ name: "select", ids: ["OBJ-ALERT"] },
		{ name: "select", ids: ["OBJ-ALERT", "OBJ-SERVICE"] },
		{ name: "select", ids: ["OBJ-SERVICE"] },
		{ name: "select", ids: [] }
	]);
	const resetHtml = await renderToString(h(TypeList, props));
	assert.doesNotMatch(resetHtml, /aria-pressed="true"|清除筛选/);
});

test("refresh applies multiple type filters to both graph and instance queries and clears them together", async () => {
	setActivePinia(createPinia());
	const store = useStore();
	Object.assign(store.filters, { types: ["OBJ-ALERT", "OBJ-SERVICE"], domain: "OPS", q: "ALT-001", mark: "pendingReview" });
	const originals = { catalog: api.catalog, list: api.list, graph: api.graph };
	let graphParams, listParams;
	api.catalog = async () => structuredClone(catalog);
	api.list = async params => {
		listParams = params;
		return { items: [], total: 0, revision: 1 };
	};
	api.graph = async params => {
		graphParams = params;
		return { nodes: [], edges: [], total: 0, revision: 1 };
	};
	try {
		await store.refresh();
		assert.deepEqual(graphParams, {
			types: "OBJ-ALERT,OBJ-SERVICE",
			domains: "OPS",
			q: "ALT-001",
			mark: "pendingReview",
			focus: "",
			hops: 1
		});
		assert.equal(listParams.q, graphParams.q);
		assert.equal(listParams.mark, graphParams.mark);
		assert.equal(listParams.type, graphParams.types);
		assert.equal(listParams.types, undefined);
		store.filters.types = [];
		await store.refresh();
		assert.equal(listParams.type, "");
		assert.equal(graphParams.types, "");
		assert.equal(store.error, null);
	} finally {
		Object.assign(api, originals);
	}
});

test("instance borders stay in their type colour through hover and selection, while edges and arrows stay neutral", async t => {
	const previous = Object.getOwnPropertyDescriptor(globalThis, "ResizeObserver");
	Object.defineProperty(globalThis, "ResizeObserver", {
		configurable: true,
		value: class {
			observe() {}
			disconnect() {}
		}
	});
	t.after(() => {
		if (previous) Object.defineProperty(globalThis, "ResizeObserver", previous);
		else delete globalThis.ResizeObserver;
	});
	const object = {
		id: "A",
		name: "告警一",
		displayCode: "ALT-001",
		color: "#FF7B72",
		badgeLabel: "告警",
		domainId: "OPS",
		layout: { x: 0, y: 0 },
		status: "active"
	};
	const props = reactive({
		objects: [object, { ...object, id: "B", name: "服务一", color: "#3D8BF7", badgeLabel: "服务", layout: { x: 400, y: 0 } }],
		relations: [
			{ id: "R1", name: "影响服务", sourceType: "A", targetType: "B", targetCardinality: { min: 0, max: 1 }, status: "active" }
		],
		domainColorMap: new Map([["OPS", "#123456"]]),
		domainNameMap: new Map([["OPS", "运维"]]),
		highlightedIds: new Set(),
		selectedObjectIds: [],
		selectedObjectId: undefined,
		edgeColor: presentation.FACT_EDGE_COLOR
	});
	let state;
	const app = renderer.createApp({
		setup() {
			state = Canvas.setup(props, { emit() {}, expose() {} });
			return () => h("div");
		}
	});
	app.provide(ssrContextKey, { modules: new Set() });
	app.mount({});
	t.after(() => app.unmount());
	const graph = graphs.at(-1);
	const cells = graph.json.cells;
	const first = cells.find(cell => cell.id === "A");
	assert.equal(first.attrs.body.stroke, "#FF7B72");
	assert.equal(first.attrs.domainLabel.text, "告警");
	const edge = cells.find(cell => cell.shape === "edge");
	assert.equal(edge.attrs.line.stroke, presentation.FACT_EDGE_COLOR);
	assert.equal(edge.attrs.line.targetMarker.fill, presentation.FACT_EDGE_COLOR);
	assert.equal(edge.labels[0].attrs.label.fill, "#86909C");
	const drawn = {
		id: "A",
		attr(path, value) {
			if (typeof path === "string") this.stroke = value;
			else this.stroke = path.body.stroke;
		},
		portProp() {}
	};
	graph.testNodes = [drawn];
	graph.events["node:mouseenter"]({ node: drawn });
	graph.events["node:mouseleave"]({ node: drawn });
	assert.equal(drawn.stroke, "#FF7B72");
	props.selectedObjectId = "A";
	await nextTick();
	props.selectedObjectId = undefined;
	await nextTick();
	assert.equal(drawn.stroke, "#FF7B72");
	assert.equal(state.buildNode({ ...object, color: undefined, badgeLabel: undefined }, []).attrs.body.stroke, "#E5EAF2");
});
