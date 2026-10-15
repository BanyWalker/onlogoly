import assert from "node:assert/strict";
import { after, before, test } from "node:test";
import { createRenderer, h, nextTick, reactive, ssrContextKey } from "vue";
import { createServer } from "vite";

let server;
let Editor;
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
before(async () => {
	server = await createServer({ server: { middlewareMode: true }, appType: "custom" });
	Editor = (await server.ssrLoadModule("/src/modules/service-integration/components/ApiEditorDialog.vue")).default;
});
after(async () => {
	await server?.close();
});

function mountEditor(t, api = null) {
	const props = reactive({
		modelValue: false,
		api,
		service: { id: "API-TEST", name: "测试服务", baseUrl: "https://ops.example.invalid", timeoutMs: 5000 },
		saving: false
	});
	const events = [];
	let state;
	const app = renderer.createApp({
		setup() {
			state = Editor.setup(props, { emit: (name, value) => events.push({ name, value }), expose() {} });
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
async function open(editor) {
	editor.props.modelValue = true;
	await nextTick();
	await nextTick();
}

test("new API emits headers, path/query parameters, nested body and separate responses together", async t => {
	const editor = mountEditor(t);
	await open(editor);
	const state = editor.state;
	assert.equal(state.form.value.timeoutMs, 5000);
	state.form.value.method = "POST";
	state.form.value.path = "/hosts/{hostId}/inspections";
	await nextTick();
	assert.equal(state.paths.value[0].name, "hostId");
	state.headers.value = [
		{
			name: "X-Tenant-Id",
			in: "header",
			required: true,
			schema: { type: "string" },
			"x-value-source": "fixed",
			"x-fixed-value": "ops"
		}
	];
	state.queries.value = [{ name: "dryRun", in: "query", schema: { type: "boolean", default: false } }];
	state.requestContent.value = {
		"application/json": {
			schema: { type: "object", properties: { checks: { type: "array", items: { type: "string" } } } },
			example: { checks: ["cpu", "memory"] }
		}
	};
	state.responses.value = {
		201: {
			description: "巡检任务已创建",
			content: {
				"application/json": {
					schema: { type: "object", properties: { taskId: { type: "string" } } },
					example: { taskId: "TASK-001" }
				}
			}
		},
		400: { description: "参数无效" }
	};
	state.save();
	const saved = editor.events.find(event => event.name === "save").value;
	assert.equal(saved.contract.parameters.length, 3);
	assert.equal(saved.contract.parameters.find(item => item.in === "header")["x-fixed-value"], "ops");
	assert.equal(saved.contract.parameters.find(item => item.in === "path").required, true);
	assert.deepEqual(saved.contract.requestBody.content["application/json"].example.checks, ["cpu", "memory"]);
	assert.deepEqual(Object.keys(saved.contract.responses), ["201", "400"]);
});

test("editing clones existing contract and advanced JSON round-trip retains extensions and named examples", async t => {
	const media = { schema: { type: "array", items: { $ref: "#/components/schemas/Host" } }, examples: { healthy: { value: [] } } };
	const contract = {
		parameters: [],
		"x-owner": "ops",
		responses: { 200: { description: "成功", content: { "application/json": media } } }
	};
	const api = { id: "EP-OPS", name: "巡检", method: "GET", path: "/hosts", revision: 7, contract };
	const editor = mountEditor(t, api);
	await open(editor);
	const state = editor.state;
	state.form.value.name = "更新巡检";
	state.toggleAdvanced();
	assert.equal(state.advanced.value, true);
	const json = JSON.parse(state.contractText.value);
	json.parameters.push({ name: "limit", in: "query", schema: { type: "integer", default: 20 } });
	state.contractText.value = JSON.stringify(json);
	state.toggleAdvanced();
	assert.equal(state.advanced.value, false);
	assert.equal(state.queries.value[0].name, "limit");
	state.save();
	const saved = editor.events.find(event => event.name === "save").value;
	assert.equal(saved.revision, 7);
	assert.equal(saved.contract["x-owner"], "ops");
	assert.deepEqual(saved.contract.responses, api.contract.responses);
	assert.equal(api.name, "巡检");
	assert.equal(api.contract.parameters.length, 0);
});

test("invalid advanced JSON and missing path definitions block save with actionable feedback", async t => {
	const editor = mountEditor(t);
	await open(editor);
	const state = editor.state;
	state.toggleAdvanced();
	state.contractText.value = "{";
	state.save();
	assert.equal(editor.events.length, 0);
	assert.ok(state.formError.value);
	state.contractText.value = JSON.stringify({ parameters: [] });
	state.form.value.path = "/hosts/{hostId}";
	await nextTick();
	state.save();
	assert.equal(editor.events.length, 0);
	assert.match(state.formError.value, /hostId/);
	assert.equal(state.tab.value, "input");
});

test("switching method preserves an existing body and reopening starts with a clean draft", async t => {
	const editor = mountEditor(t);
	await open(editor);
	const state = editor.state;
	state.form.value.method = "POST";
	await nextTick();
	state.requestContent.value = { "application/json": { schema: { type: "string" }, example: "keep" } };
	state.form.value.method = "GET";
	await nextTick();
	assert.equal(state.requestContent.value["application/json"].example, "keep");
	editor.props.modelValue = false;
	await nextTick();
	await open(editor);
	assert.equal(state.form.value.method, "GET");
	assert.deepEqual(state.requestContent.value, {});
	assert.deepEqual(state.headers.value, []);
});

test("an existing malformed contract opens in advanced mode for repair instead of rendering broken fields", async t => {
	const api = {
		id: "EP-LEGACY",
		method: "GET",
		path: "/hosts",
		contract: { parameters: { legacy: true }, "x-original": "keep" }
	};
	const editor = mountEditor(t, api);
	await open(editor);
	assert.equal(editor.state.advanced.value, true);
	assert.match(editor.state.formError.value, /parameters/);
	assert.equal(JSON.parse(editor.state.contractText.value)["x-original"], "keep");
	editor.state.contractText.value = JSON.stringify({ parameters: [], "x-original": "keep" });
	editor.state.toggleAdvanced();
	assert.equal(editor.state.advanced.value, false);
	editor.state.save();
	assert.equal(editor.events.find(event => event.name === "save").value.contract["x-original"], "keep");
});
