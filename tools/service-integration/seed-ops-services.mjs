import assert from "node:assert/strict";
import { apiServices, mcpServers } from "../../ontology/model-packages/ops/fixtures/service-integrations.mjs";

const args = process.argv.slice(2);
let baseUrl = "http://127.0.0.1:8080";
let dryRun = false;
for (let index = 0; index < args.length; index++) {
	if (args[index] === "--dry-run") dryRun = true;
	else if (args[index] === "--base-url" && args[index + 1]) baseUrl = args[++index];
	else throw new Error(`未知或不完整的参数: ${args[index]}`);
}
baseUrl = baseUrl.replace(/\/+$/, "");
assert(["http:", "https:"].includes(new URL(baseUrl).protocol), "基础地址必须是 HTTP/HTTPS");

function validateFixtures() {
	for (const records of [apiServices, mcpServers]) {
		assert.equal(new Set(records.map(item => item.id)).size, records.length, "服务标识必须唯一");
		for (const record of records) {
			for (const field of ["id", "name", "definition", "owner", "domainId"]) assert(record[field]?.trim(), `${record.id} 缺少 ${field}`);
			assert.equal(record.domainId, "DOMAIN-OPS");
			assert.equal(record.status, "DRAFT");
			assert.equal(record.enabled, false);
			assert.equal(record.auth.configured, false);
			assert(new URL(record.baseUrl ?? record.endpointUrl).hostname.endsWith(".example.invalid"));
			const children = record.endpoints ?? record.tools;
			assert.equal(new Set(children.map(item => item.id ?? item.toolKey)).size, children.length, `${record.id} 的接口或工具标识重复`);
			for (const item of children) {
				assert(["READ", "WRITE"].includes(item.operationNature));
				if (item.path) {
					assert.equal(item.status, "DRAFT");
					assert.equal(item.lastTest, null);
					assert(item.path.startsWith("/"));
					for (const match of item.path.matchAll(/\{([^}]+)\}/g)) assert(item.contract.parameters.some(parameter => parameter.name === match[1] && parameter.in === "path" && parameter.required), `${item.id} 缺少路径参数 ${match[1]}`);
				} else {
					assert.equal(item.enabled, false);
					for (const name of item.inputSchema.required) assert(item.inputSchema.properties[name], `${item.name} 的必填参数未定义: ${name}`);
				}
			}
		}
	}
}

async function request(path, body) {
	const response = await fetch(`${baseUrl}/api/v1/integrations${path}`, {
		method: body ? "POST" : "GET",
		headers: body ? { "Content-Type": "application/json; charset=utf-8" } : {},
		body: body ? JSON.stringify(body) : undefined,
		signal: AbortSignal.timeout(15000)
	});
	const payload = await response.json();
	if (!response.ok) throw new Error(`${path}: HTTP ${response.status} ${payload.message ?? payload.error?.message ?? "请求失败"}`);
	assert(Object.hasOwn(payload, "data"), `${path} 响应缺少 data`);
	return payload.data;
}

async function seed() {
	validateFixtures();
	console.log(`数据校验通过：${apiServices.length} 个 API 服务 / ${apiServices.reduce((count, item) => count + item.endpoints.length, 0)} 个接口；${mcpServers.length} 个 MCP 服务 / ${mcpServers.reduce((count, item) => count + item.tools.length, 0)} 个工具。`);
	if (dryRun) return;
	// 通过正在运行的后端写入，让内存与持久化文件保持一致；重复执行跳过现有定义。
	const existingApis = new Set((await request("/api-services")).map(item => item.id));
	const existingMcps = new Set((await request("/mcp-servers")).map(item => item.id));
	let createdApis = 0, createdEndpoints = 0, createdMcps = 0;
	for (const { endpoints, ...service } of apiServices) {
		const servicePath = `/api-services/${encodeURIComponent(service.id)}`;
		if (!existingApis.has(service.id)) {
			await request("/api-services", service);
			createdApis++;
		}
		const stored = await request(servicePath);
		// 标识被其他定义占用时保护用户数据，不向该服务追加示例接口。
		if (stored.source !== service.source) {
			console.log(`跳过已有服务 ${service.id}（非本批合成数据）。`);
			continue;
		}
		const existingEndpoints = new Set(stored.endpoints.map(item => item.id));
		for (const endpoint of endpoints) {
			if (existingEndpoints.has(endpoint.id)) continue;
			await request(`${servicePath}/endpoints`, endpoint);
			createdEndpoints++;
		}
	}
	for (const server of mcpServers) {
		if (existingMcps.has(server.id)) continue;
		await request("/mcp-servers", server);
		createdMcps++;
	}
	const apis = await request("/api-services");
	const mcps = await request("/mcp-servers");
	console.log(`新增：${createdApis} 个 API 服务、${createdEndpoints} 个接口、${createdMcps} 个 MCP 服务。`);
	console.log(`当前目录：${apis.length} 个 API 服务、${mcps.length} 个 MCP 服务。`);
}

seed().catch(error => { console.error(error.message); process.exitCode = 1; });
