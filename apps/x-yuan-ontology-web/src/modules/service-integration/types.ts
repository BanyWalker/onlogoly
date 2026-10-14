export type ApiServiceStatus = "DRAFT" | "PUBLISHED";

export interface ApiContract {
	parameters?: Array<Record<string, unknown>>;
	requestBody?: Record<string, unknown>;
	responses?: Record<string, unknown>;
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
}
