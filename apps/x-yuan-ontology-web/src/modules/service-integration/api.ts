import axios, { AxiosError } from "axios";

import type { ApiCallLog, ApiDefinition, ApiError, ApiMapping, ApiService, ApiSnapshot, ApiTestResult } from "./types";

const http = axios.create({ baseURL: "/api/v1/integrations", timeout: 15000 });

// The backend keeps its endpoint routes and fields; the frontend uses API terminology.
type ApiServiceResponse = Omit<ApiService, "apis" | "apiCount" | "verifiedApiCount"> & {
	endpoints: ApiDefinition[];
	endpointCount?: number;
	verifiedEndpointCount?: number;
};

function toApiService(response: ApiServiceResponse): ApiService {
	const { endpoints, endpointCount, verifiedEndpointCount, ...service } = response;
	return { ...service, apis: endpoints, apiCount: endpointCount, verifiedApiCount: verifiedEndpointCount };
}

function toServiceRequest(service: Partial<ApiService>): Partial<ApiServiceResponse> {
	const { apis, apiCount, verifiedApiCount, ...body } = service;
	return { ...body, endpoints: apis, endpointCount: apiCount, verifiedEndpointCount: verifiedApiCount };
}

function toApiMessage(message: string): string {
	return message.replace(/\s*端点\s*/g, " API ").trim();
}

function toApiError(error: unknown): ApiError {
	const axiosError = error as AxiosError<ApiError>;
	return axiosError.response?.data?.code
		? { ...axiosError.response.data, message: toApiMessage(axiosError.response.data.message) }
		: { code: "NETWORK_ERROR", message: axiosError.message || "服务不可用", retryable: true };
}

async function request<T>(call: () => Promise<{ data: { data: T } }>) {
	try {
		return (await call()).data.data;
	}
	catch (error) {
		throw toApiError(error);
	}
}

export const apiServiceApi = {
	async list(params?: { q?: string; status?: string }) {
		const services = await request<ApiServiceResponse[]>(() => http.get("/api-services", { params }));
		return services.map(toApiService);
	},
	async get(id: string) {
		return toApiService(await request<ApiServiceResponse>(() => http.get(`/api-services/${encodeURIComponent(id)}`)));
	},
	async create(body: Partial<ApiService>) {
		return toApiService(await request<ApiServiceResponse>(() => http.post("/api-services", toServiceRequest(body))));
	},
	async update(id: string, body: Partial<ApiService>) {
		return toApiService(await request<ApiServiceResponse>(() => http.patch(`/api-services/${encodeURIComponent(id)}`, toServiceRequest(body))));
	},
	async publish(id: string, revision?: number) {
		return toApiService(await request<ApiServiceResponse>(() => http.post(`/api-services/${encodeURIComponent(id)}/publish`, { revision })));
	},
	async unpublish(id: string, revision?: number) {
		return toApiService(await request<ApiServiceResponse>(() => http.post(`/api-services/${encodeURIComponent(id)}/unpublish`, { revision })));
	},
	async enable(id: string, revision?: number) {
		return toApiService(await request<ApiServiceResponse>(() => http.post(`/api-services/${encodeURIComponent(id)}/enable`, { revision })));
	},
	async disable(id: string, revision?: number) {
		return toApiService(await request<ApiServiceResponse>(() => http.post(`/api-services/${encodeURIComponent(id)}/disable`, { revision })));
	},
	async remove(id: string, revision?: number) {
		return toApiService(await request<ApiServiceResponse>(() => http.delete(`/api-services/${encodeURIComponent(id)}`, { data: { revision } })));
	},
	createApi(serviceId: string, body: Partial<ApiDefinition>) {
		return request<ApiDefinition>(() => http.post(`/api-services/${encodeURIComponent(serviceId)}/endpoints`, body));
	},
	updateApi(serviceId: string, apiId: string, body: Partial<ApiDefinition>) {
		return request<ApiDefinition>(() => http.patch(`/api-services/${encodeURIComponent(serviceId)}/endpoints/${encodeURIComponent(apiId)}`, body));
	},
	deleteApi(serviceId: string, apiId: string) {
		return request<ApiDefinition>(() => http.delete(`/api-services/${encodeURIComponent(serviceId)}/endpoints/${encodeURIComponent(apiId)}`));
	},
	async testApi(serviceId: string, apiId: string) {
		const result = await request<ApiTestResult>(() => http.post(`/api-services/${encodeURIComponent(serviceId)}/endpoints/${encodeURIComponent(apiId)}/test`));
		return { ...result, message: toApiMessage(result.message) };
	},
	snapshots(serviceId: string) {
		return request<ApiSnapshot[]>(() => http.get(`/api-services/${encodeURIComponent(serviceId)}/snapshots`));
	},
	createSnapshot(serviceId: string) {
		return request<ApiSnapshot>(() => http.post(`/api-services/${encodeURIComponent(serviceId)}/snapshots`));
	},
	callLogs(serviceId: string) {
		return request<ApiCallLog[]>(() => http.get("/api-services/call-log", { params: { serviceId } }));
	},
	mappings(serviceId?: string) {
		return request<ApiMapping[]>(() => http.get("/api-services/mappings", { params: serviceId ? { serviceId } : undefined }));
	},
	createMapping(body: Partial<ApiMapping> & { id: string; serviceId: string; apiId: string; targetRef: string }) {
		return request<ApiMapping>(() => http.post("/api-services/mappings", body));
	},
	updateMapping(id: string, body: Partial<ApiMapping>) {
		return request<ApiMapping>(() => http.patch(`/api-services/mappings/${encodeURIComponent(id)}`, body));
	},
	removeMapping(id: string) {
		return request<ApiMapping>(() => http.delete(`/api-services/mappings/${encodeURIComponent(id)}`));
	},
	async importOpenApi(service: Partial<ApiService>, document: Record<string, unknown>) {
		return toApiService(await request<ApiServiceResponse>(() => http.post("/api-services/import", { service: toServiceRequest(service), document })));
	}
};
