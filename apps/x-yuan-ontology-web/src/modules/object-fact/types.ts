import type { AttributeDef, DomainDef, ObjectTypeDetail, RelationDetail } from "@/modules/object-model/types";

export type FactRole = "MODELER" | "VIEWER";
export interface FactTypeItem {
	id: string;
	name: string;
	color: string;
	instanceCount: number;
}
export interface FactMark {
	markKind: "pendingReview" | "invalidRef";
	modelElementRef: string;
	reason: string;
	markedAt: string;
}
export interface FactSummary {
	instanceId: string;
	instanceKey?: string;
	objectTypeRef: string;
	typeName: string;
	domainId: string;
	domainName: string;
	domainColor: string;
	displayName: string;
	businessIdentity: Record<string, unknown>;
	businessIdentityText: string;
	sourceRef: string;
	batchId?: string;
	marks: FactMark[];
	createdAt?: string;
	updatedAt?: string;
}
export interface FactRelation {
	relationId: string;
	relationTypeRef: string;
	relationName: string;
	sourceInstanceId: string;
	targetInstanceId: string;
	sourceRef: string;
	batchId?: string;
}
export interface FactReference extends FactRelation {
	other: FactSummary;
}
export interface FactDetail extends FactSummary {
	propertyValues: Record<string, unknown>;
	referenceValues: Record<string, FactSummary[]>;
	outbound: FactReference[];
	inbound: FactReference[];
	revision: number;
}
export interface Revisioned {
	revision: number;
	instanceCount: number;
	relationCount: number;
}
export interface FactList extends Revisioned {
	items: FactSummary[];
	total: number;
	page: number;
	pageSize: number;
}
export interface FactGraph extends Revisioned {
	nodes: FactSummary[];
	edges: FactRelation[];
	total: number;
	truncated: boolean;
}
export interface FactCatalog extends Revisioned {
	types: (ObjectTypeDetail & { instanceCount: number })[];
	relations: RelationDetail[];
	domains: DomainDef[];
	lastCheck?: FactCheck;
}
export interface FactViolation {
	instanceKey?: string;
	fieldPath: string;
	rule: string;
	message: string;
	conflicts?: string[];
}
export interface FactError {
	code: string;
	message: string;
	details: FactViolation[];
	retryable: boolean;
}
export type RelationOp =
	| { op: "add"; relationTypeRef: string; sourceInstanceId?: string; targetInstanceId: string }
	| { op: "remove"; relationId: string };
export interface FactEdit {
	propertyValues?: Record<string, unknown>;
	unsetProperties?: string[];
	relationOps?: RelationOp[];
	deleteInstance?: boolean;
}
export interface FactCreate {
	objectTypeRef: string;
	propertyValues: Record<string, unknown>;
}
export interface FactImport {
	batchId: string;
	result: "ACCEPTED" | "REJECTED";
	instanceCount: number;
	relationCount: number;
	errors: FactViolation[];
	revision: number;
}
export interface FactCheck {
	pendingReview: string[];
	invalidRef: string[];
	checkedAt: string;
	checkedBy: string;
	checkedCount: number;
	revision: number;
}
export interface FactRecord {
	operationId: string;
	recordKind: "IMPORT" | "PURGE" | "EDIT" | "INSTANCE_DELETE" | "INSTANCE_CREATE";
	operatedAt: string;
	operatedBy: string;
	batchId?: string;
	sourceRef?: string;
	instanceRef?: string;
	result?: string;
	instanceCount?: number;
	relationCount?: number;
	errorCount?: number;
	errorDigest?: FactViolation[];
	changeSummary?: FactEdit;
}
export type FactAttribute = AttributeDef;
