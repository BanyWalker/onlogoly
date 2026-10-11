/**
 * 对象建模 TS 类型（单一类型源，对齐 TDD-OM-002 §5 JSON 结构与 §9 API 契约）。
 */

export type DataType = "String" | "Integer" | "Decimal" | "Boolean" | "DateTime" | "Reference" | "Enumeration";

export type CompositionKind = "Association" | "Aggregation" | "Composition";

export type NullMeaning = "UNKNOWN" | "NOT_PROVIDED" | "NOT_APPLICABLE";

export const DATA_TYPES: DataType[] = ["String", "Integer", "Decimal", "Boolean", "DateTime", "Reference", "Enumeration"];

export const COMPOSITION_KINDS: CompositionKind[] = ["Association", "Aggregation", "Composition"];

export const NULL_MEANINGS: NullMeaning[] = ["UNKNOWN", "NOT_PROVIDED", "NOT_APPLICABLE"];

export interface Cardinality {
	min: number;
	max: number | "N";
}

export interface DomainDef {
	domainId: string;
	name: string;
	description?: string | null;
	ownerRole: string;
	color?: string;
	objectCount?: number;
}

export interface AllowedValue {
	code: string;
	meaning: string;
	deprecated: boolean;
	replacedBy?: string | null;
}

export interface AttributeDef {
	id: string;
	name: string;
	dataType: DataType;
	unit?: string | null;
	allowedValues?: AllowedValue[];
	defaultValue?: unknown;
	required: boolean;
	nullable: boolean;
	nullMeaning?: NullMeaning | null;
	cardinality: Cardinality;
	referenceTarget?: string[];
	relationRef?: string | null;
	derivedFrom?: { sources: string[]; note?: string } | null;
	readOnlyNature?: string | null;
	valueSemantics?: string | null;
	termRefs?: string[];
}

export interface IdentitySpec {
	attributes: string[];
	mode: "SINGLE" | "COMPOSITE";
	uniqueScope?: string | null;
	source?: string | null;
	missingMeaning?: string | null;
}

export interface StaticConstraint {
	id: string;
	constraintType: "Static" | "RuleReference";
	location?: string | null;
	declaration?: Record<string, unknown> | null;
	ruleRef?: string | null;
	termRefs?: string[];
}

export interface ObjectTypeDetail {
	id: string;
	name: string;
	definition: string;
	scope?: string | null;
	boundary?: { includes?: string | null; excludes?: string | null } | null;
	domainId: string;
	identitySpec: IdentitySpec;
	attributes: AttributeDef[];
	staticConstraints: StaticConstraint[];
	termRefs: string[];
	graph?: { x: number; y: number };
	completeness: "draft" | "complete";
	status: "active" | "deprecated";
	revision: number;
	changeReason?: string | null;
	owner?: string;
	source?: string[];
	createdBy?: string;
	createdAt?: string;
	updatedBy?: string;
	updatedAt?: string;
}

export interface WholePart {
	wholeEnd: string;
	partEnd: string;
	partIndependent: boolean;
}

export interface RelationDetail {
	id: string;
	name: string;
	definition?: string | null;
	sourceType: string;
	targetType: string | string[];
	sourceCardinality: Cardinality;
	targetCardinality: Cardinality;
	inverse?: string | null;
	direction?: string;
	compositionKind: CompositionKind;
	wholePart?: WholePart | null;
	condition?: string | null;
	temporalValidity?: string | null;
	crossDomainDeclared?: boolean;
	domainId: string;
	termRefs?: string[];
	annotation?: { label?: string; showLabel?: boolean } | null;
	status: "active" | "deprecated";
	revision: number;
	changeReason?: string | null;
}

export interface GraphObject {
	id: string;
	name: string;
	domainId: string;
	identityAttrNames: string[];
	attributeCount?: number;
	completeness: "draft" | "complete";
	status: "active" | "deprecated";
	revision: number;
	layout: { x: number; y: number };
}

export interface GraphRelation {
	id: string;
	name: string;
	sourceType: string;
	targetType: string | string[];
	sourceCardinality: Cardinality;
	targetCardinality: Cardinality;
	compositionKind: CompositionKind;
	inverse?: string;
	annotation?: { label?: string; showLabel?: boolean } | null;
	status: "active" | "deprecated";
	revision: number;
}

export interface GraphData {
	layoutRevision: number;
	domains: DomainDef[];
	objects: GraphObject[];
	relations: GraphRelation[];
}

export interface ApiError {
	code: string;
	message: string;
	details: { field: string; value: string | null; elementId: string | null }[];
	retryable: boolean;
}

export function cardinalityText(cardinality: Cardinality | undefined): string {
	if (!cardinality) {
		return "";
	}
	return `${cardinality.min}..${cardinality.max}`;
}

export function relationTargetIds(relation: GraphRelation | RelationDetail): string[] {
	return Array.isArray(relation.targetType) ? relation.targetType : [relation.targetType];
}
