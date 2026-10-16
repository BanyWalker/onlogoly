import axios, { type AxiosError } from "axios";
import type {
	FactCatalog,
	FactCheck,
	FactCreate,
	FactDetail,
	FactEdit,
	FactError,
	FactGraph,
	FactImport,
	FactList,
	FactRecord,
	FactRole
} from "./types";

const http = axios.create({ baseURL: "/api/v1/facts", timeout: 30000 });

async function request<T>(call: () => Promise<{ data: { data: T } }>): Promise<T> {
	try {
		return (await call()).data.data;
	} catch (error) {
		const response = (error as AxiosError<FactError>).response?.data;
		throw response?.code
			? response
			: ({
					code: "NETWORK_ERROR",
					message: "对象事实服务暂时无法访问，请检查后端服务后重试",
					details: [],
					retryable: true
				} satisfies FactError);
	}
}
const headers = (role: FactRole, revision?: number) => ({
	"X-Xiyuan-Role": role,
	...(revision === undefined ? {} : { "If-Match": revision })
});

export const factApi = {
	catalog: () => request<FactCatalog>(() => http.get("/object-types")),
	list: (params: { type?: string; domain?: string; q?: string; mark?: string; page?: number; pageSize?: number } = {}) =>
		request<FactList>(() => http.get("/instances", { params })),
	graph: (
		params: { types?: string; domains?: string; q?: string; mark?: string; focus?: string; hops?: number; limit?: number } = {}
	) => request<FactGraph>(() => http.get("/graph", { params })),
	detail: (id: string) => request<FactDetail>(() => http.get(`/instances/${encodeURIComponent(id)}`)),
	create: (body: FactCreate, role: FactRole, revision: number) =>
		request<FactDetail>(() => http.post("/instances", body, { headers: headers(role, revision) })),
	edit: (id: string, body: FactEdit, role: FactRole, revision: number) =>
		request<{ revision: number }>(() =>
			http.patch(`/instances/${encodeURIComponent(id)}`, body, { headers: headers(role, revision) })
		),
	delete: (id: string, role: FactRole, revision: number) =>
		request<{ revision: number }>(() =>
			http.delete(`/instances/${encodeURIComponent(id)}`, { headers: headers(role, revision) })
		),
	import: (body: unknown, role: FactRole, revision: number) =>
		request<FactImport>(() => http.post("/import", body, { headers: headers(role, revision) })),
	check: (role: FactRole, revision: number) =>
		request<FactCheck>(() => http.post("/check", {}, { headers: headers(role, revision) })),
	purge: (role: FactRole, revision: number) =>
		request<{ revision: number }>(() => http.delete("/data", { data: { confirm: true }, headers: headers(role, revision) })),
	batches: (page = 1) =>
		request<{ items: FactRecord[]; total: number }>(() => http.get("/batches", { params: { page, pageSize: 30 } }))
};
