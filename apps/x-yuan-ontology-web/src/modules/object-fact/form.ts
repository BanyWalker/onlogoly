import type { AttributeDef, ObjectTypeDetail, RelationDetail } from "@/modules/object-model/types";
import type { FactSummary } from "./types";

export const nullMeaningLabels: Record<string, string> = { UNKNOWN: "未知", NOT_PROVIDED: "未提供", NOT_APPLICABLE: "不适用" };

export function nullValue(value: unknown): boolean {
	return value === null || (typeof value === "object" && value !== null && "value" in value && value.value === null);
}
export function displayValue(value: unknown): string {
	if (value === undefined) return "未填写";
	if (nullValue(value)) {
		const reason = value && typeof value === "object" && "nullReason" in value ? String(value.nullReason) : "";
		return nullMeaningLabels[reason] ?? "空值";
	}
	if (typeof value === "boolean") return value ? "是" : "否";
	if (Array.isArray(value)) return value.map(displayValue).join("、");
	return typeof value === "object" ? JSON.stringify(value) : String(value);
}
export function multiple(attribute: AttributeDef): boolean {
	return attribute.cardinality?.max === "N" || (attribute.cardinality?.max ?? 1) > 1;
}
export function initialFields(type: ObjectTypeDetail | undefined, values?: Record<string, unknown>) {
	const result: Record<string, unknown> = {};
	for (const attribute of type?.attributes ?? []) {
		if (attribute.dataType === "Reference") continue;
		const value = values ? values[attribute.name] : attribute.defaultValue;
		if (value !== undefined) result[attribute.name] = JSON.parse(JSON.stringify(value));
	}
	// Keep removed properties visible in the draft; saving must not silently drop them.
	if (values)
		for (const [key, value] of Object.entries(values)) if (!(key in result)) result[key] = JSON.parse(JSON.stringify(value));
	return result;
}
export function propertyValues(type: ObjectTypeDetail | undefined, fields: Record<string, unknown>) {
	const result: Record<string, unknown> = {};
	for (const attribute of type?.attributes ?? []) {
		if (attribute.dataType === "Reference") continue;
		const value = fields[attribute.name];
		if (value === undefined || value === "") {
			if (value === "" && attribute.dataType === "String") result[attribute.name] = "";
			continue;
		}
		if (multiple(attribute) && !nullValue(value)) {
			let parsed: unknown;
			try {
				parsed = typeof value === "string" ? JSON.parse(value) : value;
			} catch {
				throw new Error(`${attribute.name} 必须填写有效的 JSON 数组`);
			}
			if (!Array.isArray(parsed)) throw new Error(`${attribute.name} 必须填写 JSON 数组`);
			result[attribute.name] = parsed.map(item => {
				if (attribute.dataType === "Integer" || attribute.dataType === "Decimal") {
					const number = Number(item);
					if (
						(typeof item !== "string" && typeof item !== "number") ||
						String(item).trim() === "" ||
						!Number.isFinite(number) ||
						(attribute.dataType === "Integer" && !Number.isInteger(number))
					)
						throw new Error(`${attribute.name} 中每个值都必须是${attribute.dataType === "Integer" ? "整数" : "数值"}`);
					return number;
				}
				if (attribute.dataType === "Boolean") {
					if (item !== true && item !== false && item !== "true" && item !== "false")
						throw new Error(`${attribute.name} 中每个值都必须为是或否`);
					return item === true || item === "true";
				}
				return item;
			});
		} else result[attribute.name] = value;
	}
	return result;
}
export function changedValues(before: Record<string, unknown>, after: Record<string, unknown>, type?: ObjectTypeDetail) {
	const changed: Record<string, unknown> = {};
	for (const name of new Set([...Object.keys(before), ...Object.keys(after)])) {
		if (JSON.stringify(before[name]) === JSON.stringify(after[name])) continue;
		const attribute = type?.attributes.find(item => item.name === name);
		if (after[name] === undefined && attribute?.nullable)
			changed[name] = attribute.nullMeaning ? { value: null, nullReason: attribute.nullMeaning } : null;
		else changed[name] = after[name] ?? null;
	}
	return changed;
}
export function matchingRelations(
	relations: RelationDetail[],
	source: Pick<FactSummary, "objectTypeRef">,
	target: Pick<FactSummary, "objectTypeRef">
) {
	return relations.filter(
		relation =>
			relation.status !== "deprecated" &&
			relation.sourceType === source.objectTypeRef &&
			(Array.isArray(relation.targetType) ? relation.targetType : [relation.targetType]).includes(target.objectTypeRef)
	);
}
