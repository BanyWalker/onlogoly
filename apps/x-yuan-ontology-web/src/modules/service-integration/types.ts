export type ApiServiceStatus = "DRAFT" | "PUBLISHED";

export interface ApiSchema {
	type?: string;
	properties?: Record<string, ApiSchema>;
	required?: string[];
	items?: ApiSchema;
	description?: string;
	format?: string;
	nullable?: boolean;
	default?: unknown;
	example?: unknown;
	enum?: unknown[];
	[key: string]: unknown;
}

export interface ApiParameter {
	name?: string;
	in?: string;
	required?: boolean;
	description?: string;
	schema?: ApiSchema;
	example?: unknown;
	[key: string]: unknown;
}

export interface ApiMediaType {
	schema?: ApiSchema;
	example?: unknown;
	[key: string]: unknown;
}

export interface ApiRequestBody {
	required?: boolean;
	description?: string;
	content?: Record<string, ApiMediaType>;
	[key: string]: unknown;
}

export interface ApiResponse {
	description?: string;
	content?: Record<string, ApiMediaType>;
	headers?: Record<string, ApiParameter>;
	[key: string]: unknown;
}

export interface ApiContract {
	parameters?: ApiParameter[];
	requestBody?: ApiRequestBody;
	responses?: Record<string, ApiResponse>;
	[key: string]: unknown;
}

export interface ApiAuthConfig {
	authType?: "INHERIT" | "NONE" | "API_KEY" | "BEARER" | "BASIC" | "CUSTOM_HEADER";
	inherit?: boolean;
	apiKeyName?: string;
	apiKeyIn?: "header" | "query";
	apiKeyValue?: string;
	token?: string;
	username?: string;
	password?: string;
	headerName?: string;
	headerValue?: string;
	[key: string]: unknown;
}

export interface ApiDebugRequest {
	path?: Record<string, string>;
	query?: Record<string, string>;
	headers?: Record<string, string>;
	body?: unknown;
	auth?: ApiAuthConfig;
	variables?: Record<string, string>;
}

export interface ApiDefinition {
	id: string;
	name: string;
	definition: string;
	method: string;
	path: string;
	operationNature: "READ" | "WRITE";
	timeoutMs: number;
	status: ApiServiceStatus;
	enabled: boolean;
	revision: number;
	contract?: ApiContract;
	lastTest?: {
		outcome: "SUCCESS" | "FAILED";
		category: string;
		message: string;
		latencyMs: number;
		at: string;
		request?: { method: string; url: string; headers?: Record<string, string>; body?: unknown };
		response?: { status: number; headers?: Record<string, string>; body?: string };
	} | null;
}

export interface ApiService {
	id: string;
	name: string;
	definition: string;
	domainId: string;
	owner: string;
	source?: string;
	version: string;
	baseUrl: string;
	environment: string;
	timeoutMs: number;
	status: ApiServiceStatus;
	enabled: boolean;
	revision: number;
	auth?: { authType: string; configured: boolean; masked?: string };
	apis: ApiDefinition[];
	apiCount?: number;
	verifiedApiCount?: number;
	createdAt?: string;
	updatedAt?: string;
}

export interface ApiError {
	code: string;
	message: string;
	details?: Array<{ field?: string; value?: string; reference?: string }>;
	retryable?: boolean;
}

export interface ApiTestResult {
	outcome: "SUCCESS" | "FAILED";
	category: string;
	message: string;
	latencyMs: number;
	at: string;
	request?: { method: string; url: string; headers?: Record<string, string>; body?: unknown };
	response?: { status: number; headers?: Record<string, string>; body?: string };
}

export interface ApiSnapshot {
	snapshotId: number;
	capturedAt: string;
	revision: number;
	apiCount: number;
	apis: ApiDefinition[];
	diff?: { added: number; removed: number; changed: number };
}

export interface ApiCallLog {
	callId: string;
	serviceId: string;
	apiId: string;
	apiName?: string;
	resultStatus: "SUCCESS" | "FAILED";
	normalizedError?: string;
	category?: string;
	message?: string;
	latencyMs: number;
	occurredAt: string;
}

export interface ApiMapping {
	id: string;
	serviceId: string;
	apiId: string;
	targetRef: string;
	mappingKind: string;
	description?: string;
	status: string;
	createdAt?: string;
}
