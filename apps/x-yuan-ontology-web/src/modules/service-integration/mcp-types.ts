export type McpServiceStatus = "DRAFT" | "PUBLISHED";
export type McpOperationNature = "READ" | "WRITE";

export interface McpTool {
	name: string;
	toolKey: string;
	description?: string;
	inputSchema: Record<string, unknown>;
	enabled: boolean;
	operationNature: McpOperationNature;
	changeStatus?: "UNCHANGED" | "ADDED" | "REMOVED" | "SCHEMA_CHANGED";
	pendingReview?: boolean;
	revision?: number;
}

export interface McpTestResult {
	outcome: "SUCCESS" | "FAILED";
	normalizedError?: string | null;
	message?: string;
	protocolVersion?: string;
	latencyMs?: number;
	tools?: McpTool[];
	category?: string;
	[other: string]: unknown;
}

export interface McpSnapshot {
	snapshotId: number;
	discoveredAt: string;
	protocolVersion: string;
	tools: McpTool[];
}

export interface McpRefreshRecord {
	recordId: string;
	occurredAt: string;
	outcome: "SUCCESS" | "FAILED";
	normalizedError?: string | null;
	message?: string;
	protocolVersion?: string;
	latencyMs?: number;
	snapshotId?: number;
	diff?: { added: number; removed: number; schemaChanged: number };
}

export interface McpServer {
	id: string;
	name: string;
	definition: string;
	domainId: string;
	owner: string;
	endpointUrl: string;
	transport: "HTTP";
	protocolVersion?: string;
	timeoutMs: number;
	status: McpServiceStatus;
	enabled: boolean;
	revision: number;
	auth?: { authType: string; configured: boolean; masked?: string };
	tools: McpTool[];
	snapshots: McpSnapshot[];
	refreshRecords?: McpRefreshRecord[];
	lastTest?: McpTestResult | null;
	currentSnapshotId?: number;
	toolCount?: number;
	enabledToolCount?: number;
	pendingReviewCount?: number;
	createdAt?: string;
	updatedAt?: string;
}

export interface McpCallLog {
	callId: string;
	serverId: string;
	toolKey: string;
	resultStatus: "SUCCESS" | "FAILED" | "REJECTED";
	normalizedError?: string | null;
	latencyMs: number;
	occurredAt: string;
	paramsDigest?: unknown;
}

export interface McpMapping {
	id: string;
	serverId: string;
	toolKey: string;
	targetRef: string;
	mappingKind: string;
	description?: string;
	status: string;
	createdAt?: string;
}

export interface McpInvokeResult {
	resultStatus: "SUCCESS" | "FAILED" | "REJECTED";
	normalizedError?: string | null;
	validationMessage?: string;
	latencyMs: number;
	pendingReview?: boolean;
	externalContent?: boolean;
	responseSource?: string;
	response?: unknown;
}

export interface McpApiError {
	code: string;
	message: string;
	details?: unknown;
	retryable?: boolean;
}
