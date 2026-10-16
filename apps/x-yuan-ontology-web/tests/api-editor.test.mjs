import assert from "node:assert/strict";
import { after, before, test } from "node:test";
import { createRenderer, h, nextTick, reactive, ssrContextKey } from "vue";
import { createServer } from "vite";

let server;
let Editor;
let ContentEditor;
let ParameterEditor;
let ParameterSettings;
let SchemaFields;
let Debugger;
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
	ContentEditor = (await server.ssrLoadModule("/src/modules/service-integration/components/ApiContentEditor.vue")).default;
	ParameterEditor = (await server.ssrLoadModule("/src/modules/service-integration/components/ApiParameterEditor.vue")).default;
	ParameterSettings = (await server.ssrLoadModule("/src/modules/service-integration/components/ApiParameterSettings.vue"))
		.default;
	SchemaFields = (await server.ssrLoadModule("/src/modules/service-integration/components/ApiSchemaFields.vue")).default;
	Debugger = (await server.ssrLoadModule("/src/modules/service-integration/components/ApiDebugDialog.vue")).default;
});
after(async () => {
	await server?.close();
});

function mountEditor(t, api = null) {
	return mountComponent(t, Editor, {
		modelValue: false,
		api,
		service: { id: "API-TEST", name: "测试服务", baseUrl: "https://ops.example.invalid", timeoutMs: 5000 },
		saving: false
	});
}
function mountComponent(t, component, initialProps) {
	const props = reactive(initialProps);
	const events = [];
	let state;
	const app = renderer.createApp({
		setup() {
			state = component.setup(props, {
				emit: (name, value) => {
					events.push({ name, value });
					if (name === "update:modelValue") props.modelValue = value;
				},
				expose() {}
			});
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
	state.form.value.name = "创建巡检";
	state.form.value.definition = "为主机创建巡检任务";
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
	const api = { id: "EP-OPS", name: "巡检", definition: "查询巡检记录", method: "GET", path: "/hosts", revision: 7, contract };
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
	Object.assign(state.form.value, { name: "查询主机", definition: "根据标识查询主机", path: "/hosts" });
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
		name: "旧接口",
		definition: "兼容旧配置",
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

test("basic validation rejects blank required fields and invalid URLs, while an explicit root path is valid", async t => {
	const editor = mountEditor(t);
	await open(editor);
	const state = editor.state;
	assert.equal(state.form.value.path, "");
	state.save();
	assert.equal(editor.events.length, 0);
	assert.ok(state.basicErrors.value.name);
	assert.ok(state.basicErrors.value.definition);
	assert.ok(state.basicErrors.value.path);
	Object.assign(state.form.value, { name: "查询", definition: "查询服务", path: "/hosts?status=up" });
	state.save();
	assert.ok(state.basicErrors.value.path);
	state.form.value.path = "/";
	state.save();
	assert.equal(editor.events.find(event => event.name === "save").value.path, "/");
});

test("method changes suggest operation nature until the user overrides, and GET bodies require an explicit decision", async t => {
	const editor = mountEditor(t);
	await open(editor);
	const state = editor.state;
	Object.assign(state.form.value, { name: "查询", definition: "批量查询", path: "/search", method: "POST" });
	await nextTick();
	assert.equal(state.form.value.operationNature, "WRITE");
	state.form.value.operationNature = "READ";
	state.natureOverridden.value = true;
	state.form.value.method = "PUT";
	await nextTick();
	assert.equal(state.form.value.operationNature, "READ");
	state.requestContent.value = { "application/json": { schema: { type: "string" }, example: "keep" } };
	state.form.value.method = "GET";
	await nextTick();
	assert.equal(state.bodyAllowed.value, false);
	state.save();
	assert.equal(editor.events.length, 0);
	assert.equal(state.inputTab.value, "body");
	assert.match(state.formError.value, /显式开启/);
	state.setCustomBody(true);
	state.save();
	const saved = editor.events.find(event => event.name === "save").value;
	assert.equal(saved.contract["x-allow-request-body"], true);
	assert.equal(saved.contract.requestBody.content["application/json"].example, "keep");
});

test("clearing request content also clears required and Path renames preserve metadata without leaving orphan parameters", async t => {
	const editor = mountEditor(t);
	await open(editor);
	const state = editor.state;
	state.setBodyRequired(true);
	assert.equal(state.contract.value.requestBody.required, false);
	Object.assign(state.form.value, { method: "POST", path: "/hosts/{hostId}" });
	await nextTick();
	state.paths.value[0].schema = { type: "integer", minimum: 1 };
	state.paths.value[0].description = "主机标识";
	state.requestContent.value = { "application/json": { schema: { type: "object", properties: {} } } };
	state.setBodyRequired(true);
	assert.equal(state.contract.value.requestBody.required, true);
	state.requestContent.value = {};
	assert.equal(state.contract.value.requestBody.required, false);
	state.form.value.path = "/hosts/{id}";
	await nextTick();
	assert.equal(state.paths.value[0].name, "id");
	assert.equal(state.paths.value[0].schema.minimum, 1);
	assert.equal(state.paths.value[0].description, "主机标识");
	state.form.value.path = "/hosts";
	await nextTick();
	assert.deepEqual(state.paths.value, []);
});

test("legacy multi-format request opens for visual selection but cannot save before normalization", async t => {
	const api = {
		id: "EP-MULTI",
		name: "导入",
		definition: "导入资产",
		method: "POST",
		path: "/imports",
		contract: {
			requestBody: {
				content: { "application/json": { schema: { type: "object" } }, "text/plain": { schema: { type: "string" } } }
			}
		}
	};
	const editor = mountEditor(t, api);
	await open(editor);
	assert.equal(editor.state.advanced.value, false);
	editor.state.save();
	assert.equal(editor.events.length, 0);
	assert.match(editor.state.formError.value, /一种格式/);
});

test("request format switching preserves compatible fields, and destructive changes can be cancelled", async t => {
	const editor = mountComponent(t, ContentEditor, {
		single: true,
		modelValue: {
			"application/json": {
				schema: { type: "object", properties: { cluster: { type: "string", description: "集群", default: "prod" } } },
				example: { cluster: "prod" }
			}
		}
	});
	const state = editor.state;
	state.selectFormat("application/x-www-form-urlencoded");
	await nextTick();
	assert.deepEqual(Object.keys(editor.props.modelValue), ["application/x-www-form-urlencoded"]);
	assert.equal(editor.props.modelValue["application/x-www-form-urlencoded"].schema.properties.cluster.description, "集群");
	state.selectFormat("multipart/form-data");
	await nextTick();
	state.patchMedia({
		schema: { type: "object", properties: { cluster: { type: "string" }, file: { type: "string", format: "binary" } } }
	});
	await nextTick();
	state.selectFormat("application/json");
	assert.equal(state.pendingFormat.value, "application/json");
	assert.match(state.pendingLosses.value.join(" "), /file/);
	state.pendingFormat.value = null;
	assert.ok(editor.props.modelValue["multipart/form-data"].schema.properties.file);
	state.selectFormat("application/json");
	state.confirmFormat();
	await nextTick();
	assert.deepEqual(Object.keys(editor.props.modelValue), ["application/json"]);
	assert.equal(editor.props.modelValue["application/json"].schema.properties.file, undefined);
	assert.ok(editor.props.modelValue["application/json"].schema.properties.cluster);
});

test("choosing one imported format preserves its original configuration until confirmed", async t => {
	const json = { schema: { type: "object" }, example: {} };
	const editor = mountComponent(t, ContentEditor, {
		single: true,
		modelValue: { "application/json": json, "text/plain": { schema: { type: "string" }, example: "text" } }
	});
	assert.equal(editor.state.activeFormat.value, "");
	editor.state.selectFormat("application/json");
	assert.equal(Object.keys(editor.props.modelValue).length, 2);
	editor.state.confirmFormat();
	await nextTick();
	assert.deepEqual(editor.props.modelValue, { "application/json": json });
});

test("JSON inference merges rules by default, previews replacements, and generation never overwrites on cancel", async t => {
	const editor = mountComponent(t, ContentEditor, {
		single: true,
		modelValue: {
			"application/json": {
				schema: {
					type: "object",
					required: ["status"],
					properties: { status: { type: "string", description: "状态", enum: ["up", "down"], default: "up" } }
				},
				example: { status: "down" }
			}
		}
	});
	const state = editor.state;
	state.openExample("infer");
	state.exampleText.value = '{"status":"up","count":3}';
	assert.equal(state.inferredSchema.value.properties.status.description, "状态");
	assert.deepEqual(state.inferredSchema.value.required, ["status"]);
	state.inferenceMode.value = "replace";
	assert.equal(state.inferredSchema.value.properties.status.enum, undefined);
	state.inferenceMode.value = "merge";
	state.applyExample();
	await nextTick();
	assert.equal(editor.props.modelValue["application/json"].schema.properties.status.default, "up");
	assert.equal(editor.props.modelValue["application/json"].example.count, 3);
	state.generateExample();
	assert.equal(state.exampleVisible.value, true);
	state.exampleVisible.value = false;
	assert.equal(editor.props.modelValue["application/json"].example.count, 3);
});

test("schema edits require example review and reject keeping an incompatible example", async t => {
	const editor = mountComponent(t, ContentEditor, {
		single: true,
		modelValue: { "application/json": { schema: { type: "string" }, example: "ops" } }
	});
	const state = editor.state;
	state.patchMedia({ schema: { type: "integer" } });
	await nextTick();
	assert.equal(editor.props.modelValue["application/json"]["x-example-review-required"], true);
	state.keepExample();
	assert.ok(state.reviewError.value);
	state.openExample("edit");
	state.exampleText.value = "7";
	state.applyExample();
	await nextTick();
	assert.equal(editor.props.modelValue["application/json"].example, 7);
	assert.equal(editor.props.modelValue["application/json"]["x-example-review-required"], undefined);
});

test("text examples accept unquoted content, multipart fields use binary schemas, and Path cannot be manually added or deleted", async t => {
	const text = mountComponent(t, ContentEditor, { single: true, modelValue: { "text/plain": { schema: { type: "string" } } } });
	text.state.openExample("edit");
	text.state.exampleText.value = "service=ops\nstatus=up";
	text.state.applyExample();
	assert.equal(text.props.modelValue["text/plain"].example, "service=ops\nstatus=up");
	const fields = mountComponent(t, SchemaFields, {
		modelValue: { type: "object", properties: { archive: { type: "string", description: "诊断包" } } },
		flat: true,
		allowFiles: true,
		depth: 0
	});
	fields.state.setFieldType("archive", fields.props.modelValue.properties.archive, "files");
	assert.deepEqual(fields.props.modelValue.properties.archive.items, { type: "string", format: "binary" });
	assert.equal(fields.props.modelValue.properties.archive.description, "诊断包");
	const parameters = mountComponent(t, ParameterEditor, {
		modelValue: [{ name: "id", in: "path", required: true }],
		location: "path",
		lockedNames: ["id"],
		response: false
	});
	parameters.state.addParameter();
	parameters.state.removeParameter(0);
	parameters.state.renameParameter(0, "newId");
	assert.equal(parameters.events.length, 0);
});

test("parameter settings write to the API draft directly and retain defaults without a second apply step", t => {
	const editor = mountComponent(t, ParameterSettings, {
		modelValue: { name: "limit", in: "query", schema: { type: "integer", default: 10 }, "x-source": "imported" },
		location: "query"
	});
	editor.state.schema.value = { ...editor.state.schema.value, default: 25 };
	editor.state.setRequired(true);
	assert.equal(editor.props.modelValue.schema.default, 25);
	assert.equal(editor.props.modelValue.required, true);
	assert.equal(editor.props.modelValue["x-source"], "imported");
});

test("parameter popover validates JSON Schema before applying and preserves parameter metadata", t => {
	const editor = mountComponent(t, ParameterSettings, {
		modelValue: {
			name: "filter",
			in: "query",
			description: "筛选条件",
			schema: { type: "string", default: "active" },
			example: "active"
		},
		location: "query"
	});
	editor.state.schemaText.value = '{"type":';
	editor.state.applyJson();
	assert.ok(editor.state.jsonError.value);
	assert.equal(editor.events.length, 0);
	editor.state.schemaText.value = '{"type":"array","items":{"type":"string"},"minItems":1}';
	editor.state.applyJson();
	assert.equal(editor.state.jsonError.value, "");
	assert.deepEqual(editor.props.modelValue.schema, { type: "array", items: { type: "string" }, minItems: 1 });
	assert.equal(editor.props.modelValue.description, "筛选条件");
	assert.equal(editor.props.modelValue.example, "active");
});

test("Path requirement is locked in both row actions and the parameter popover", t => {
	const parameter = { name: "id", in: "path", required: true, schema: { type: "string" } };
	const table = mountComponent(t, ParameterEditor, { modelValue: [parameter], location: "path", lockedNames: ["id"] });
	table.state.setRequired(0, false);
	assert.equal(table.events.length, 0);
	const settings = mountComponent(t, ParameterSettings, { modelValue: parameter, location: "path" });
	settings.state.setRequired(false);
	assert.equal(settings.events.length, 0);
	settings.state.schema.value = { type: "string", nullable: true };
	assert.equal(settings.props.modelValue.required, true);
	assert.equal(settings.props.modelValue.schema.nullable, true);
});

test("save and debug validates the draft and preserves request authentication through advanced JSON", async t => {
	const editor = mountEditor(t);
	await open(editor);
	editor.state.save(true);
	assert.equal(editor.events.length, 0);
	Object.assign(editor.state.form.value, { name: "查询主机", definition: "读取主机详情", path: "/hosts" });
	editor.state.authDraft.value = { authType: "BEARER", token: "test-token" };
	editor.state.toggleAdvanced();
	assert.equal(JSON.parse(editor.state.contractText.value)["x-auth"].token, "test-token");
	editor.state.toggleAdvanced();
	editor.state.save(true);
	assert.equal(editor.events[0].name, "save-debug");
	assert.equal(editor.events[0].value.contract["x-auth"].token, "test-token");
});

test("debug sends selected parameters, resolves the URL preview, and keeps the API definition unchanged", async t => {
	const api = {
		id: "API-DEBUG",
		method: "GET",
		path: "/hosts/{id}",
		contract: {
			parameters: [
				{ name: "id", in: "path", required: true, example: "host/01", schema: { type: "string" } },
				{ name: "cluster", in: "query", example: "{{cluster}}", schema: { type: "string" } },
				{ name: "unused", in: "query", example: "omit" },
				{ name: "X-Tenant", in: "header", "x-fixed-value": "ops" }
			],
			"x-auth": { authType: "BEARER", token: "test-token" }
		}
	};
	const original = structuredClone(api);
	const debug = mountComponent(t, Debugger, { modelValue: false, api, service: { baseUrl: "https://example.invalid" } });
	await open(debug);
	debug.state.enabled.value["query:unused"] = false;
	debug.state.variableValues.value.cluster = "生产 集群";
	assert.equal(
		debug.state.requestUrl.value,
		"https://example.invalid/hosts/host%2F01?cluster=%E7%94%9F%E4%BA%A7+%E9%9B%86%E7%BE%A4"
	);
	debug.state.send();
	const request = debug.events.find(event => event.name === "send").value;
	assert.deepEqual(request.query, { cluster: "{{cluster}}" });
	assert.deepEqual(request.headers, { "X-Tenant": "ops" });
	assert.equal(request.auth.token, "test-token");
	debug.state.auth.value.token = "override";
	debug.state.reset();
	assert.equal(debug.state.auth.value.token, "test-token");
	assert.deepEqual(api, original);
});

test("debug blocks malformed JSON, supports primitive JSON examples, and resets when reopened", async t => {
	const debug = mountComponent(t, Debugger, {
		modelValue: false,
		api: {
			id: "API-BODY",
			method: "POST",
			path: "/echo",
			contract: { requestBody: { content: { "application/json": { example: "hello" } } } }
		}
	});
	await open(debug);
	assert.equal(debug.state.bodyText.value, '"hello"');
	debug.state.bodyText.value = '{"broken":';
	debug.state.send();
	assert.equal(debug.events.length, 0);
	assert.match(debug.state.localError.value, /JSON/);
	assert.equal(debug.state.tab.value, "body");
	debug.state.bodyText.value = '{"valid":true}';
	debug.state.send();
	assert.deepEqual(debug.events[0].value.body, { valid: true });
	debug.props.modelValue = false;
	await nextTick();
	await open(debug);
	assert.equal(debug.state.localError.value, "");
	assert.equal(debug.state.tab.value, "params");
	assert.equal(debug.state.bodyText.value, '"hello"');
});
