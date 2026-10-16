import assert from "node:assert/strict";
import test from "node:test";

import {
	copyJson,
	convertContentFormat,
	createContract,
	displayValue,
	exampleFromSchema,
	inputValue,
	parameterSchema,
	parseContract,
	replaceParameterGroup,
	schemaFromExample,
	syncPathParameters,
	validateContract,
	validateSchema
} from "../src/modules/service-integration/api-contract.ts";
import type { ApiContract } from "../src/modules/service-integration/types.ts";

test("replacing headers preserves imported references, other parameters and schema extensions", () => {
	const original: ApiContract = {
		"x-catalog": { owner: "ops" },
		parameters: [
			{ $ref: "#/components/parameters/Tenant" },
			{ name: "hostId", in: "path", required: true, schema: { type: "string" } },
			{ name: "X-Trace-Id", in: "header", schema: { type: "string", "x-format": "trace" } }
		],
		requestBody: { $ref: "#/components/requestBodies/Inspection" },
		responses: {
			"200": {
				description: "ok",
				content: {
					"application/json": {
						schema: { oneOf: [{ $ref: "#/components/schemas/Host" }, { type: "array", items: { type: "string" } }] },
						examples: { ops: { value: [] } }
					}
				},
				"x-extra": true
			}
		}
	};
	const edited = copyJson(original);
	edited.parameters = replaceParameterGroup(edited.parameters!, "header", [
		{ ...edited.parameters![2], description: "请求链路标识" }
	]);
	const reopened = parseContract(JSON.stringify(edited));
	assert.deepEqual(reopened.responses, original.responses);
	assert.deepEqual(reopened.requestBody, original.requestBody);
	assert.deepEqual(reopened.parameters?.slice(0, 2), original.parameters?.slice(0, 2));
	assert.equal(reopened.parameters?.[2].schema?.["x-format"], "trace");
	assert.deepEqual(reopened["x-catalog"], original["x-catalog"]);
	assert.equal(validateContract(reopened, "/hosts/{hostId}"), null);
});

test("path synchronization derives unique required parameters and prunes removed URL placeholders", () => {
	const parameters = [
		{ name: "hostId", in: "path", required: true, schema: { type: "integer" }, example: 7 },
		{ name: "status", in: "query", schema: { type: "string" } }
	];
	const synced = syncPathParameters(parameters, "/hosts/{hostId}/checks/{checkId}/{checkId}");
	assert.equal(synced.length, 3);
	assert.deepEqual(synced[0], parameters[0]);
	assert.equal(synced.find(parameter => parameter.name === "checkId")?.required, true);
	assert.equal(validateContract({ parameters: synced }, "/hosts/{hostId}/checks/{checkId}"), null);
	assert.match(validateContract({ parameters: synced }, "/checks/{checkId}")!.message, /路径参数 hostId/);
	const pruned = syncPathParameters(synced, "/checks/{checkId}");
	assert.equal(
		pruned.find(parameter => parameter.name === "hostId"),
		undefined
	);
	assert.equal(pruned.find(parameter => parameter.name === "status")?.in, "query");
});

test("request body format is singular, form and text schemas match their editors, and samples obey constraints", () => {
	assert.equal(
		validateContract({ requestBody: { content: { "application/json": {}, "text/plain": {} } } }, "/")?.code,
		"MULTIPLE_REQUEST_FORMATS"
	);
	assert.match(validateContract({ requestBody: { required: true } }, "/")!.message, /无请求体/);
	assert.match(
		validateContract({ requestBody: { content: { "text/plain": { schema: { type: "object" } } } } }, "/")!.message,
		/string/
	);
	assert.match(
		validateContract(
			{
				requestBody: {
					content: {
						"application/x-www-form-urlencoded": { schema: { type: "object", properties: { nested: { type: "object" } } } }
					}
				}
			},
			"/"
		)!.message,
		/不适用于/
	);
	assert.match(validateSchema({ type: "integer", minimum: 10, maximum: 1 })!, /下限/);
	assert.match(validateSchema({ type: "string", pattern: "[" })!, /正则/);
	assert.match(validateSchema({ type: "integer", enum: ["up"] })!, /枚举值/);
	assert.match(validateSchema({ type: "string", minLength: 5, example: "a" })!, /示例值/);
	assert.match(
		validateContract(
			{
				responses: {
					200: { content: { "application/json": { schema: { type: "integer" }, examples: { invalid: { value: "text" } } } } }
				}
			},
			"/"
		)!.message,
		/命名示例 invalid/
	);
});

test("format conversions preserve source data and enumerate incompatible fields before replacement", () => {
	const source = {
		schema: { type: "object", required: ["nested"], properties: { id: { type: "integer" }, nested: { type: "object" } } },
		example: { id: 7, nested: {} }
	};
	const result = convertContentFormat(source, "application/json", "application/x-www-form-urlencoded");
	assert.equal(result.losses.length, 1);
	assert.match(result.losses[0], /nested/);
	assert.deepEqual(result.media?.schema?.required, []);
	assert.deepEqual(result.media?.example, { id: 7 });
	assert.ok(source.schema.properties.nested);
});

test("examples generate optional nested schemas and preserve primitive roots", () => {
	const example = { hosts: [{ id: 1, healthy: false }], total: 0, note: "", metadata: null };
	const schema = schemaFromExample(example);
	assert.equal(schema.required, undefined);
	assert.equal(schema.properties?.hosts.items?.properties?.healthy.type, "boolean");
	assert.equal(validateSchema(schema), null);
	assert.equal(validateContract({ responses: { "200": { content: { "application/json": { schema, example } } } } }, "/"), null);
	assert.deepEqual(exampleFromSchema({ type: "array", items: { type: "integer", example: 0 } }), [0]);
	assert.equal(exampleFromSchema({ type: "boolean", example: false }), false);
	assert.equal(exampleFromSchema({ type: "integer", minimum: 1 }), 1);
	assert.equal(exampleFromSchema({ type: "string", minLength: 3 }), "aaa");
	assert.deepEqual(exampleFromSchema({ type: "array", items: { type: "integer" }, minItems: 2 }), [0, 0]);
	assert.equal(displayValue(false), "false");
	assert.equal(inputValue("0", { type: "integer" }), 0);
	assert.equal(inputValue("001", { type: "string" }), "001");
	assert.equal(validateSchema(schemaFromExample([1, "two"])), null);
});

test("invalid contract fields and parameter types are reported without saving", () => {
	assert.throws(() => parseContract("[]"));
	assert.throws(() => parseContract('{"parameters":{}}'));
	assert.equal(
		validateContract(
			{
				parameters: [
					{ name: "X-Tenant", in: "header" },
					{ name: "x-tenant", in: "header" }
				]
			},
			"/"
		)?.tab,
		"headers"
	);
	assert.match(
		validateContract({ parameters: [{ name: "limit", in: "query", schema: { type: "integer" }, example: "abc" }] }, "/")!.message,
		/示例值/
	);
	assert.match(validateSchema({ type: "object", required: ["missing"], properties: {} })!, /必填字段/);
	assert.match(validateSchema({ type: "array" })!, /数组元素/);
	assert.match(
		validateContract(
			{ parameters: [{ name: "X-Fixed", in: "header", "x-value-source": "fixed", "x-fixed-value": "a\r\nb" }] },
			"/"
		)!.message,
		/换行/
	);
});

test("responses retain separate status definitions and reject a body for 204", () => {
	const contract = createContract();
	contract.responses!["204"] = { description: "deleted" };
	contract.responses!["400"] = {
		description: "bad request",
		content: {
			"application/json": {
				schema: { type: "object", properties: { message: { type: "string" } } },
				example: { message: "invalid id" }
			}
		}
	};
	assert.equal(validateContract(contract, "/"), null);
	contract.responses!["204"].content = { "application/json": { schema: { type: "string" } } };
	assert.match(validateContract(contract, "/")!.message, /204/);
	assert.equal(parameterSchema({ name: "status", in: "query", type: "string" }).type, "string");
	assert.equal(validateContract({ parameters: [{ name: "status", in: "query", type: "string" }] }, "/"), null);
});
