import type { ApiContract, ApiMediaType, ApiParameter, ApiSchema } from "./types";

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

export function syncPathParameters(parameters: ApiParameter[], path: string, previousPath?: string): ApiParameter[] {
	const names = pathParameterNames(path);
	const previousNames = pathParameterNames(previousPath ?? "");
	const definitions = new Map(
		parameters.filter(parameter => parameter.in === "path").map(parameter => [parameter.name, parameter])
	);
	const paths = names.map((name, index) => {
		const previousName = previousNames[index];
		const previous =
			definitions.get(name) ?? (previousName && !names.includes(previousName) ? definitions.get(previousName) : undefined);
		return previous ? { ...previous, name, required: true } : { name, in: "path", required: true, schema: { type: "string" } };
	});
	return replaceParameterGroup(parameters, "path", paths);
}

export function suggestedOperationNature(method?: string): "READ" | "WRITE" {
	return ["POST", "PUT", "PATCH", "DELETE"].includes(method ?? "") ? "WRITE" : "READ";
}

export function isBodyMethod(method?: string): boolean {
	return !["GET", "HEAD"].includes(method ?? "GET");
}

export function mergeSchemas(existing: ApiSchema, inferred: ApiSchema): ApiSchema {
	if (isAdvancedSchema(existing)) return copyJson(existing);
	if (existing.type && inferred.type && existing.type !== inferred.type) return copyJson(existing);
	const merged: ApiSchema = { ...copyJson(inferred), ...copyJson(existing) };
	if (inferred.properties || existing.properties) {
		merged.properties = { ...copyJson(inferred.properties ?? {}), ...copyJson(existing.properties ?? {}) };
		for (const [name, schema] of Object.entries(inferred.properties ?? {})) {
			if (existing.properties?.[name]) merged.properties[name] = mergeSchemas(existing.properties[name], schema);
		}
	}
	if (inferred.items && existing.items) merged.items = mergeSchemas(existing.items, inferred.items);
	return merged;
}

export function schemaSignature(schema: ApiSchema): string {
	const rules = { ...schema };
	for (const key of ["description", "example", "examples", "default"]) delete rules[key];
	if (schema.properties)
		rules.properties = Object.fromEntries(
			Object.entries(schema.properties).map(([name, child]) => [name, JSON.parse(schemaSignature(child))])
		);
	if (schema.items) rules.items = JSON.parse(schemaSignature(schema.items));
	return JSON.stringify(rules);
}

export function contentKind(format: string): "json" | "form" | "files" | "text" | "other" {
	if (format === "application/json" || format.endsWith("+json")) return "json";
	if (format === "application/x-www-form-urlencoded") return "form";
	if (format === "multipart/form-data") return "files";
	if (format.startsWith("text/")) return "text";
	return "other";
}

export function isFileSchema(schema: ApiSchema): boolean {
	return schema.format === "binary" || (schema.type === "array" && schema.items?.format === "binary");
}

export function convertContentFormat(
	source: ApiMediaType | undefined,
	from: string,
	to: string
): { media?: ApiMediaType; losses: string[] } {
	const populated =
		source &&
		(Object.keys(source.schema?.properties ?? {}).length > 0 ||
			Object.keys(source.schema ?? {}).some(key => !["type", "properties"].includes(key)) ||
			source.example !== undefined ||
			source.examples !== undefined ||
			isAdvancedSchema(source.schema ?? {}) ||
			(source.schema?.type && source.schema.type !== "object"));
	if (!to) return { losses: populated ? ["现有请求体结构和示例将被清空"] : [] };
	if (from === to && source) return { media: copyJson(source), losses: [] };
	const target = contentKind(to);
	if (!source)
		return { media: { schema: target === "text" ? { type: "string" } : { type: "object", properties: {} } }, losses: [] };
	const media = copyJson(source);
	const losses: string[] = [];
	const schema = media.schema ?? {};
	if (target === "text") {
		if (schema.type !== "string" && populated) losses.push("原字段结构将转换为纯文本");
		media.schema = schema.type === "string" ? schema : { type: "string", description: schema.description };
		if (media.example !== undefined && typeof media.example !== "string") media.example = JSON.stringify(media.example, null, 2);
		if (media.examples) losses.push("命名示例将移除");
		delete media.examples;
	} else if (target === "form" || target === "files") {
		if (isAdvancedSchema(schema) || schema.type !== "object") {
			if (populated) losses.push("原结构不能转换为表单字段，将新建空表单");
			media.schema = { type: "object", properties: {} };
			delete media.example;
			delete media.examples;
		} else {
			const properties = { ...schema.properties };
			for (const [name, field] of Object.entries(properties)) {
				if (
					(target === "files" && isFileSchema(field)) ||
					(["string", "integer", "number", "boolean"].includes(field.type ?? "") &&
						!isFileSchema(field) &&
						!isAdvancedSchema(field))
				)
					continue;
				delete properties[name];
				losses.push(`字段 ${name} 无法转换为该表单格式，将移除`);
			}
			media.schema = {
				...schema,
				type: "object",
				properties,
				required: schema.required?.filter(name => Object.hasOwn(properties, name))
			};
			if (isObject(media.example))
				media.example = Object.fromEntries(Object.entries(media.example).filter(([name]) => Object.hasOwn(properties, name)));
			if (losses.length && media.examples) {
				delete media.examples;
				losses.push("命名示例将移除");
			}
		}
	} else if (target === "json" && contentKind(from) === "files") {
		const properties = { ...schema.properties };
		for (const [name, field] of Object.entries(properties))
			if (isFileSchema(field)) {
				delete properties[name];
				losses.push(`文件字段 ${name} 将移除`);
			}
		media.schema = { ...schema, properties, required: schema.required?.filter(name => Object.hasOwn(properties, name)) };
		if (isObject(media.example))
			media.example = Object.fromEntries(Object.entries(media.example).filter(([name]) => Object.hasOwn(properties, name)));
		if (losses.length && media.examples) {
			delete media.examples;
			losses.push("命名示例将移除");
		}
	}
	return { media, losses };
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
		const count = typeof schema.minItems === "number" ? schema.minItems : schema.maxItems === 0 ? 0 : 1;
		if (count > 100) return undefined;
		return item === undefined ? [] : Array.from({ length: count }, () => copyJson(item));
	}
	if (schema.type === "boolean") return false;
	if (schema.type === "integer" || schema.type === "number") {
		const minimum =
			typeof schema.minimum === "number" ? (schema.type === "integer" ? Math.ceil(schema.minimum) : schema.minimum) : -Infinity;
		const maximum =
			typeof schema.maximum === "number" ? (schema.type === "integer" ? Math.floor(schema.maximum) : schema.maximum) : Infinity;
		return minimum > maximum ? undefined : Math.min(maximum, Math.max(minimum, 0));
	}
	if (schema.type === "string") {
		let example = schema.format === "date" ? "2026-01-01" : schema.format === "date-time" ? "2026-01-01T00:00:00Z" : "";
		if (typeof schema.minLength === "number") {
			if (schema.minLength > 10000) return undefined;
			example = example.padEnd(schema.minLength, "a");
		}
		if (typeof schema.maxLength === "number") example = example.slice(0, schema.maxLength);
		return valueMatches(example, schema) ? example : undefined;
	}
	return schema.nullable ? null : undefined;
}

export function valueMatches(value: unknown, schema: ApiSchema): boolean {
	if (value === null) return schema.nullable === true || schema.type === undefined;
	if (schema.enum && !schema.enum.some(item => JSON.stringify(item) === JSON.stringify(value))) return false;
	if (
		typeof value === "number" &&
		((typeof schema.minimum === "number" && value < schema.minimum) ||
			(typeof schema.maximum === "number" && value > schema.maximum))
	)
		return false;
	if (typeof value === "string") {
		if (
			(typeof schema.minLength === "number" && value.length < schema.minLength) ||
			(typeof schema.maxLength === "number" && value.length > schema.maxLength)
		)
			return false;
		if (typeof schema.pattern === "string") {
			try {
				if (!new RegExp(schema.pattern).test(value)) return false;
			} catch {
				return false;
			}
		}
	}
	if (
		Array.isArray(value) &&
		((typeof schema.minItems === "number" && value.length < schema.minItems) ||
			(typeof schema.maxItems === "number" && value.length > schema.maxItems))
	)
		return false;
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
	if (Array.isArray(schema.enum) && schema.enum.some(value => !valueMatches(value, { ...schema, enum: undefined } as ApiSchema)))
		return `${label}的枚举值与字段类型或范围不一致`;
	for (const field of ["minimum", "maximum", "minLength", "maxLength", "minItems", "maxItems"]) {
		if (schema[field] !== undefined && (typeof schema[field] !== "number" || !Number.isFinite(schema[field])))
			return `${label}的 ${field} 必须是数字`;
	}
	for (const [min, max] of [
		["minimum", "maximum"],
		["minLength", "maxLength"],
		["minItems", "maxItems"]
	]) {
		if (typeof schema[min] === "number" && typeof schema[max] === "number" && schema[min] > schema[max])
			return `${label}的下限不能大于上限`;
	}
	for (const key of ["minLength", "maxLength", "minItems", "maxItems"])
		if (schema[key] !== undefined && (!Number.isInteger(schema[key]) || (schema[key] as number) < 0))
			return `${label}的 ${key} 必须是非负整数`;
	if (typeof schema.pattern === "string") {
		try {
			new RegExp(schema.pattern);
		} catch {
			return `${label}的正则表达式无效`;
		}
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
	code?: string;
	section?: "path" | "query" | "body";
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
			if (media["x-example-review-required"]) return `${label}结构已变更，请更新或确认保留现有示例`;
			if (
				["form", "files"].includes(contentKind(format)) &&
				isObject(media.schema) &&
				!isAdvancedSchema(media.schema as ApiSchema) &&
				media.schema.type !== "object"
			)
				return `${label}的表单根节点必须为 object`;
			if (
				contentKind(format) === "text" &&
				isObject(media.schema) &&
				!isAdvancedSchema(media.schema as ApiSchema) &&
				media.schema.type !== "string"
			)
				return `${label}的文本格式必须使用 string 类型`;
			if (["form", "files"].includes(contentKind(format)) && isObject(media.schema) && isObject(media.schema.properties)) {
				for (const [name, field] of Object.entries(media.schema.properties)) {
					if (!isObject(field)) return `${label}的字段 ${name} 配置无效`;
					if (isAdvancedSchema(field as ApiSchema)) continue;
					if (contentKind(format) === "files" && isFileSchema(field as ApiSchema)) continue;
					if (!["string", "integer", "number", "boolean"].includes(String(field.type)) || isFileSchema(field as ApiSchema))
						return `${label}的字段 ${name} 不适用于当前表单格式，请调整类型或切换为 JSON`;
				}
			}
			if (media.schema !== undefined) {
				const error = validateSchema(media.schema, label);
				if (error) return error;
				if (media.example !== undefined && !valueMatches(media.example, media.schema as ApiSchema))
					return `${label}示例与字段结构不一致`;
				if (isObject(media.examples))
					for (const [name, sample] of Object.entries(media.examples)) {
						if (isObject(sample) && sample.value !== undefined && !valueMatches(sample.value, media.schema as ApiSchema))
							return `${label}的命名示例 ${name} 与字段结构不一致`;
					}
			}
		}
		return null;
	};
	const bodyError = validateContent(contract.requestBody?.content, "请求体");
	if (bodyError)
		return {
			tab: "input",
			section: "body",
			code: bodyError.includes("结构已变更") ? "EXAMPLE_REVIEW" : undefined,
			message: bodyError
		};
	if (Object.keys(contract.requestBody?.content ?? {}).length > 1)
		return {
			tab: "input",
			section: "body",
			code: "MULTIPLE_REQUEST_FORMATS",
			message: "请求体只能配置一种格式，请选择一种后保存"
		};
	if (contract.requestBody?.required && !contract.requestBody.$ref && !Object.keys(contract.requestBody.content ?? {}).length)
		return { tab: "input", section: "body", message: "无请求体时不能设置请求体必填" };
	for (const [code, response] of Object.entries(contract.responses ?? {})) {
		if (code.startsWith("x-")) continue;
		if (!/^(?:[1-5]\d{2}|[1-5]XX|default)$/.test(code) || !isObject(response))
			return { tab: "output", message: `响应状态码 ${code} 或配置无效` };
		if (response.$ref) continue;
		if (code === "204" && response.content && Object.keys(response.content).length)
			return { tab: "output", message: "204 响应不能配置响应体，请将内容类型设为无" };
		const error = validateContent(response.content, `响应 ${code}`);
		if (error) return { tab: "output", code: error.includes("结构已变更") ? "EXAMPLE_REVIEW" : undefined, message: error };
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
