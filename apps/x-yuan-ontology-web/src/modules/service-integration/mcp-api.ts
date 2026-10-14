import axios, { AxiosError } from "axios";

import type { McpApiError, McpCallLog, McpInvokeResult, McpMapping, McpServer, McpSnapshot, McpTestResult, McpTool } from "./mcp-types";

const http = axios.create({ baseURL: "/api/v1/integrations", timeout: 15000 });

function toApiError(error: unknown): McpApiError {
	const axiosError = error as AxiosError<{ code?: string; message?: string; error?: { code?: string; message?: string } }>;
	const payload = axiosError.response?.data;
	return payload?.code
		? { code: payload.code, message: payload.message || "请求失败" }
		: payload?.error?.code
			? { code: payload.error.code, message: payload.error.message || "请求失败" }
			: { code: "NETWORK_ERROR", message: axiosError.message || "服务暂不可用", retryable: true };
}

async function request<T>(call: () => Promise<{ data: { data: T } }>): Promise<T> {
	try {
		return (await call()).data.data;
	}
	catch (error) {
		throw toApiError(error);
	}
}

const path = (value: string) => encodeURIComponent(value);

export const mcpApi = {
	list(params?: { q?: string; status?: string; domainId?: string }) {
		return request<McpServer[]>(() => http.get("/mcp-servers", { params }));
	},
	get(id: string) {
		return request<McpServer>(() => http.get(`/mcp-servers/${path(id)}`));
	},
	create(body: Partial<McpServer> & { id: string }) {
		return request<McpServer>(() => http.post("/mcp-servers", body));
	},
	update(id: string, body: Partial<McpServer>) {
		return request<McpServer>(() => http.patch(`/mcp-servers/${path(id)}`, body));
	},
	publish(id: string, revision?: number) {
		return request<McpServer>(() => http.post(`/mcp-servers/${path(id)}/publish`, { revision }));
	},
	unpublish(id: string, revision?: number) {
		return request<McpServer>(() => http.post(`/mcp-servers/${path(id)}/unpublish`, { revision }));
	},
	enable(id: string, revision?: number) {
		return request<McpServer>(() => http.post(`/mcp-servers/${path(id)}/enable`, { revision }));
	},
	disable(id: string, revision?: number) {
		return request<McpServer>(() => http.post(`/mcp-servers/${path(id)}/disable`, { revision }));
	},
	remove(id: string, revision?: number) {
		return request<McpServer>(() => http.delete(`/mcp-servers/${path(id)}`, { data: { revision } }));
	},
	discover(id: string) {
		return request<McpTestResult>(() => http.post(`/mcp-servers/${path(id)}/discover`));
	},
	refresh(id: string) {
		return request<McpTestResult & { diff?: { added: number; removed: number; schemaChanged: number } }>(() => http.post(`/mcp-servers/${path(id)}/refresh`));
	},
	tools(id: string) {
		return request<McpTool[]>(() => http.get(`/mcp-servers/${path(id)}/tools`));
	},
	updateTool(id: string, toolKey: string, body: Partial<McpTool>) {
		return request<McpTool>(() => http.patch(`/mcp-servers/${path(id)}/tools/${path(toolKey)}`, body));
	},
	reviewTool(id: string, toolKey: string) {
		return request<McpTool>(() => http.post(`/mcp-servers/${path(id)}/tools/${path(toolKey)}/review`));
	},
	invoke(id: string, toolKey: string, body: { arguments: Record<string, unknown>; confirm?: boolean }) {
		return request<McpInvokeResult>(() => http.post(`/mcp-servers/${path(id)}/tools/${path(toolKey)}/invoke`, body));
	},
	snapshots(id: string) {
		return request<McpSnapshot[]>(() => http.get(`/mcp-servers/${path(id)}/snapshots`));
	},
	logs(serverId?: string) {
		return request<McpCallLog[]>(() => http.get("/call-log", { params: serverId ? { serverId } : undefined }));
	},
	mappings(serverId?: string) {
		return request<McpMapping[]>(() => http.get("/mappings", { params: serverId ? { serverId } : undefined }));
	},
	createMapping(body: Partial<McpMapping> & { id: string; serverId: string; toolKey: string; targetRef: string }) {
		return request<McpMapping>(() => http.post("/mappings", body));
	},
	updateMapping(id: string, body: Partial<McpMapping>) {
		return request<McpMapping>(() => http.patch(`/mappings/${path(id)}`, body));
	},
	removeMapping(id: string) {
		return request<McpMapping>(() => http.delete(`/mappings/${path(id)}`));
	}
};
