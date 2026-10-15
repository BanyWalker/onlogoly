import type { ApiContract, ApiParameter, ApiSchema } from "./types";

export const schemaTypes = ["string", "integer", "number", "boolean", "object", "array"];

export function isObject(value: unknown): value is Record<string, unknown> {
	return value !== null && typeof value === "object" && !Array.isArray(value);
}

export function copyJson<T>(value: T): T {
	return JSON.parse(JSON.stringify(value)) as T;
}

export function createContract(): ApiContract {
	return { parameters: [], requestBody: {}, responses: { "200": { description: "成功" } } };
}

export function parseContract(text: string): ApiContract {
	const value: unknown = JSON.parse(text);
	if (!isObject(value)) throw new Error("契约必须是 JSON 对象");
	if (value.parameters !== undefined && !Array.isArray(value.parameters)) throw new Error("parameters 必须是数组");
	if (value.requestBody !== undefined && !isObject(value.requestBody)) throw new Error("requestBody 必须是对象");
	if (value.responses !== undefined && !isObject(value.responses)) throw new Error("responses 必须是对象");
	return value as ApiContract;
}

export function parameterSchema(parameter: ApiParameter): ApiSchema {
	if (isObject(parameter.schema)) return parameter.schema;
	return typeof parameter.type === "string" ? { type: parameter.type } : {};
}

export function isAdvancedSchema(schema: ApiSchema): boolean {
	return (
		["$ref", "allOf", "anyOf", "oneOf", "not"].some(key => key in schema) ||
		(schema.type !== undefined && !schemaTypes.includes(schema.type))
	);
}

export function changeSchemaType(schema: ApiSchema, type: string): ApiSchema {
	const next: ApiSchema = { ...schema, type };
	if (type !== "object") {
		delete next.properties;
		delete next.required;
		delete next.additionalProperties;
	}
	if (type !== "array") delete next.items;
	if (type === "object") next.properties ??= {};
	if (type === "array") next.items ??= { type: "string" };
	return next;
}

export function pathParameterNames(path: string): string[] {
	return [...new Set(Array.from(path.matchAll(/\{([^{}]+)\}/g), match => match[1]))];
}

export function syncPathParameters(parameters: ApiParameter[], path: string): ApiParameter[] {
	const names = pathParameterNames(path);
	const result = parameters.map(parameter =>
		parameter.in === "path" && names.includes(parameter.name ?? "") && parameter.required !== true
			? { ...parameter, required: true }
			: parameter
	);
	for (const name of names) {
		if (!result.some(parameter => parameter.in === "path" && parameter.name === name)) {
			result.push({ name, in: "path", required: true, schema: { type: "string" } });
		}
	}
	return result;
}

export function replaceParameterGroup(parameters: ApiParameter[], location: string, rows: ApiParameter[]): ApiParameter[] {
	const remaining = [...rows];
	const result: ApiParameter[] = [];
	for (const parameter of parameters) {
		if (parameter.in !== location) result.push(parameter);
		else if (remaining.length) result.push(remaining.shift()!);
	}
	return [...result, ...remaining];
}

export function displayValue(value: unknown): string {
	return value === undefined ? "" : typeof value === "string" ? value : JSON.stringify(value);
}

export function inputValue(text: string, schema: ApiSchema): unknown {
	if (text === "") return undefined;
	if (schema.type === "string" || !schema.type) return text;
	try {
		return JSON.parse(text);
	} catch {
		return text;
	}
}

export function schemaFromExample(value: unknown): ApiSchema {
	if (value === null) return { nullable: true };
	if (Array.isArray(value)) {
		const alternatives = [
			...new Map(
				value.map(item => {
					const schema = schemaFromExample(item);
					return [JSON.stringify(schema), schema] as const;
				})
			).values()
		];
		return { type: "array", items: alternatives.length <= 1 ? (alternatives[0] ?? {}) : { oneOf: alternatives } };
	}
	if (isObject(value))
		return {
			type: "object",
			properties: Object.fromEntries(Object.entries(value).map(([name, item]) => [name, schemaFromExample(item)]))
		};
	return { type: typeof value === "number" ? (Number.isInteger(value) ? "integer" : "number") : typeof value };
}

export function exampleFromSchema(schema: ApiSchema, depth = 0): unknown {
	if (depth > 20) return undefined;
	if (schema.example !== undefined) return copyJson(schema.example);
	if (schema.default !== undefined) return copyJson(schema.default);
	if (schema.enum?.length) return copyJson(schema.enum[0]);
	if (isAdvancedSchema(schema)) return undefined;
	if (schema.type === "object")
		return Object.fromEntries(
			Object.entries(schema.properties ?? {})
				.map(([name, child]) => [name, exampleFromSchema(child, depth + 1)])
				.filter(([, value]) => value !== undefined)
		);
	if (schema.type === "array") {
		const item = exampleFromSchema(schema.items ?? {}, depth + 1);
		return item === undefined ? [] : [item];
	}
	if (schema.type === "boolean") return false;
	if (schema.type === "integer" || schema.type === "number") return 0;
	if (schema.type === "string")
		return schema.format === "date" ? "2026-01-01" : schema.format === "date-time" ? "2026-01-01T00:00:00Z" : "";
	return schema.nullable ? null : undefined;
}

function valueMatches(value: unknown, schema: ApiSchema): boolean {
	if (value === null) return schema.nullable === true || schema.type === undefined;
	if (schema.enum && !schema.enum.some(item => JSON.stringify(item) === JSON.stringify(value))) return false;
	if (schema.type === "integer") return typeof value === "number" && Number.isInteger(value);
	if (schema.type === "number") return typeof value === "number" && Number.isFinite(value);
	if (schema.type === "array") return Array.isArray(value) && value.every(item => valueMatches(item, schema.items ?? {}));
	if (schema.type === "object")
		return (
			isObject(value) &&
			(schema.required ?? []).every(key => Object.hasOwn(value, key)) &&
			Object.entries(schema.properties ?? {}).every(
				([key, child]) => !Object.hasOwn(value, key) || valueMatches(value[key], child)
			)
		);
	return !schema.type || typeof value === schema.type;
}

export function validateSchema(schema: unknown, label = "字段结构", depth = 0): string | null {
	if (!isObject(schema)) return `${label}必须是对象`;
	if (depth > 40) return `${label}嵌套层级过深`;
	if (isAdvancedSchema(schema as ApiSchema)) return null;
	if (
		schema.required !== undefined &&
		(!Array.isArray(schema.required) || schema.required.some(name => typeof name !== "string"))
	)
		return `${label}的必填字段列表无效`;
	if (schema.properties !== undefined && !isObject(schema.properties)) return `${label}的 properties 必须是对象`;
	if (schema.enum !== undefined && !Array.isArray(schema.enum)) return `${label}的枚举值必须是数组`;
	for (const field of ["minimum", "maximum", "minLength", "maxLength", "minItems", "maxItems"]) {
		if (schema[field] !== undefined && (typeof schema[field] !== "number" || !Number.isFinite(schema[field])))
			return `${label}的 ${field} 必须是数字`;
	}
	if (isObject(schema.properties)) {
		for (const [name, child] of Object.entries(schema.properties)) {
			if (!name.trim()) return `${label}存在空字段名`;
			const error = validateSchema(child, `${label}.${name}`, depth + 1);
			if (error) return error;
		}
		if ((schema.required as string[] | undefined)?.some(name => !Object.hasOwn(schema.properties!, name)))
			return `${label}的必填字段必须存在于字段列表中`;
	}
	if (schema.type === "array" && schema.items === undefined) return `${label}需要配置数组元素类型`;
	if (schema.items !== undefined) {
		const error = validateSchema(schema.items, `${label}[]`, depth + 1);
		if (error) return error;
	}
	for (const key of ["default", "example"])
		if (schema[key] !== undefined && !valueMatches(schema[key], schema as ApiSchema))
			return `${label}的${key === "default" ? "默认值" : "示例值"}与类型或枚举不一致`;
	return null;
}

export interface ContractIssue {
	tab: "headers" | "input" | "output";
	message: string;
}

export function validateContract(contract: ApiContract, path: string): ContractIssue | null {
	const seen = new Set<string>();
	const parameters = contract.parameters ?? [];
	for (const parameter of parameters) {
		if (!isObject(parameter)) return { tab: "input", message: "参数必须是对象" };
		if (parameter.$ref) continue;
		const tab = parameter.in === "header" ? "headers" : "input";
		if (
			typeof parameter.name !== "string" ||
			!parameter.name.trim() ||
			!["path", "query", "header", "cookie"].includes(parameter.in ?? "")
		)
			return { tab, message: "请填写参数名和有效的参数位置" };
		const name = parameter.in === "header" ? parameter.name.toLowerCase() : parameter.name;
		const key = `${parameter.in}:${name}`;
		if (seen.has(key)) return { tab, message: `参数 ${parameter.name} 重复` };
		seen.add(key);
		if (parameter.in === "header" && !/^[!#$%&'*+.^_`|~\w-]+$/.test(parameter.name))
			return { tab, message: `Header 名称 ${parameter.name} 无效` };
		if (parameter.in === "path" && (!parameter.required || !pathParameterNames(path).includes(parameter.name)))
			return { tab, message: `路径参数 ${parameter.name} 必须为必填且与路径占位符对应` };
		const schema = parameterSchema(parameter);
		const error = validateSchema(schema, parameter.name);
		if (error) return { tab, message: error };
		if (parameter.example !== undefined && !valueMatches(parameter.example, schema))
			return { tab, message: `${parameter.name} 的示例值与字段类型不一致` };
		if (parameter["x-value-source"] === "fixed" && /[\r\n]/.test(String(parameter["x-fixed-value"] ?? "")))
			return { tab, message: `${parameter.name} 的固定值不能包含换行` };
	}
	// Referenced parameters may contain path definitions; preserve them until refs can be resolved.
	for (const name of pathParameterNames(path))
		if (!seen.has(`path:${name}`) && !parameters.some(parameter => parameter.$ref))
			return { tab: "input", message: `请配置路径参数 ${name}` };
	const validateContent = (content: unknown, label: string): string | null => {
		if (content === undefined) return null;
		if (!isObject(content)) return `${label}的内容格式必须是对象`;
		for (const [format, media] of Object.entries(content)) {
			if (!isObject(media)) return `${label}的 ${format} 配置无效`;
			if (media.schema !== undefined) {
				const error = validateSchema(media.schema, label);
				if (error) return error;
				if (media.example !== undefined && !valueMatches(media.example, media.schema as ApiSchema))
					return `${label}示例与字段结构不一致`;
			}
		}
		return null;
	};
	const bodyError = validateContent(contract.requestBody?.content, "请求体");
	if (bodyError) return { tab: "input", message: bodyError };
	for (const [code, response] of Object.entries(contract.responses ?? {})) {
		if (code.startsWith("x-")) continue;
		if (!/^(?:[1-5]\d{2}|[1-5]XX|default)$/.test(code) || !isObject(response))
			return { tab: "output", message: `响应状态码 ${code} 或配置无效` };
		if (response.$ref) continue;
		if (code === "204" && response.content && Object.keys(response.content).length)
			return { tab: "output", message: "204 响应不能配置响应体，请将内容类型设为无" };
		const error = validateContent(response.content, `响应 ${code}`);
		if (error) return { tab: "output", message: error };
		if (response.headers !== undefined && !isObject(response.headers))
			return { tab: "output", message: `响应 ${code} 的 Header 必须是对象` };
		const headerNames = new Set<string>();
		for (const [name, header] of Object.entries(response.headers ?? {})) {
			if (!name.trim() || !isObject(header)) return { tab: "output", message: `响应 ${code} 的 Header 配置无效` };
			if (headerNames.has(name.toLowerCase())) return { tab: "output", message: `响应 Header ${name} 重复` };
			headerNames.add(name.toLowerCase());
			const error = validateSchema(parameterSchema(header), `响应 Header ${name}`);
			if (error) return { tab: "output", message: error };
		}
	}
	return null;
}
