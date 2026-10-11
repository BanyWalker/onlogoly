import axios, { AxiosError } from "axios";

import type {
	ApiError,
	AttributeDef,
	DomainDef,
	GraphData,
	ObjectTypeDetail,
	RelationDetail
} from "./types";

/**
 * 对象建模 API client（TDD-OM-002 §9.1：前缀 /api/v1，写接口 If-Match 乐观锁）。
 */
const http = axios.create({
	baseURL: "/api/v1",
	timeout: 15000
});

function unwrap<T>(payload: { requestId: string; data: T }): T {
	return payload.data;
}

function toApiError(error: unknown): ApiError {
	const axiosError = error as AxiosError<{ code?: string; message?: string; details?: ApiError["details"]; retryable?: boolean }>;
	const body = axiosError.response?.data;
	if (body?.code) {
		return {
			code: body.code,
			message: body.message ?? "请求失败",
			details: body.details ?? [],
			retryable: body.retryable ?? false
		};
	}
	return { code: "NETWORK_ERROR", message: axiosError.message || "网络或服务不可用", details: [], retryable: true };
}

async function request<T>(call: () => Promise<{ data: { requestId: string; data: T } }>): Promise<T> {
	try {
		const response = await call();
		return unwrap(response.data);
	}
	catch (error) {
		throw toApiError(error);
	}
}

export const objectModelApi = {
	fetchGraph(domainId?: string) {
		return request<GraphData>(() => http.get("/object-model/graph", { params: domainId ? { domainId } : {} }));
	},

	fetchExport() {
		return request<Record<string, unknown>>(() => http.get("/object-model/export"));
	},

	saveLayout(layoutRevision: number, positions: Record<string, { x: number; y: number }>) {
		return request<{ layoutRevision: number }>(() =>
			http.put("/object-model/layout", { positions }, { headers: { "If-Match": layoutRevision } })
		);
	},

	createObjectType(body: Partial<ObjectTypeDetail>) {
		return request<ObjectTypeDetail>(() => http.post("/object-types", body));
	},

	fetchObjectType(id: string) {
		return request<ObjectTypeDetail>(() => http.get(`/object-types/${encodeURIComponent(id)}`));
	},

	updateObjectType(id: string, body: Partial<ObjectTypeDetail>, revision: number) {
		return request<ObjectTypeDetail>(() =>
			http.patch(`/object-types/${encodeURIComponent(id)}`, body, { headers: { "If-Match": revision } })
		);
	},

	deprecateObjectType(id: string, revision: number) {
		return request<ObjectTypeDetail>(() =>
			http.delete(`/object-types/${encodeURIComponent(id)}`, { headers: { "If-Match": revision } })
		);
	},

	createAttribute(objectId: string, body: Partial<AttributeDef>) {
		return request<AttributeDef>(() => http.post(`/object-types/${encodeURIComponent(objectId)}/attributes`, body));
	},

	updateAttribute(objectId: string, attrId: string, body: Partial<AttributeDef>) {
		return request<AttributeDef>(() =>
			http.patch(`/object-types/${encodeURIComponent(objectId)}/attributes/${encodeURIComponent(attrId)}`, body)
		);
	},

	deleteAttribute(objectId: string, attrId: string) {
		return request<ObjectTypeDetail>(() =>
			http.delete(`/object-types/${encodeURIComponent(objectId)}/attributes/${encodeURIComponent(attrId)}`)
		);
	},

	createRelation(body: Partial<RelationDetail>) {
		return request<RelationDetail>(() => http.post("/relationships", body));
	},

	fetchRelation(id: string) {
		return request<RelationDetail>(() => http.get(`/relationships/${encodeURIComponent(id)}`));
	},

	updateRelation(id: string, body: Partial<RelationDetail>, revision: number) {
		return request<RelationDetail>(() =>
			http.patch(`/relationships/${encodeURIComponent(id)}`, body, { headers: { "If-Match": revision } })
		);
	},

	deleteRelation(id: string) {
		return request<RelationDetail>(() => http.delete(`/relationships/${encodeURIComponent(id)}`));
	},

	listDomains() {
		return request<DomainDef[]>(() => http.get("/domains"));
	},

	createDomain(body: { name: string; description?: string; ownerRole: string; domainId?: string }) {
		return request<DomainDef>(() => http.post("/domains", body));
	},

	deleteDomain(id: string) {
		return request<DomainDef>(() => http.delete(`/domains/${encodeURIComponent(id)}`));
	}
};
