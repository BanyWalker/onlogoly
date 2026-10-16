import type { RouteRecordRaw } from "vue-router";

export const workbenchRoutes: RouteRecordRaw[] = [
	{
		path: "ontology-modeling",
		name: "ontology-modeling",
		redirect: { name: "object-model" },
		meta: { title: "本体建模", icon: "policy", menu: true, order: 1 },
		children: [
			{ path: "/object-model", name: "object-model", component: () => import("@/modules/object-model/ObjectModelPage.vue"), meta: { title: "对象建模", icon: "objectModel", menu: true, order: 1 } },
			{ path: "/rule-modeling", name: "rule-modeling", component: () => import("@/pages/placeholder/ComingSoonPage.vue"), meta: { title: "规则建模", icon: "ruleModel", menu: true, order: 2, disabled: true, badge: "后续版本" } },
			{ path: "/terminology", name: "terminology", component: () => import("@/pages/placeholder/ComingSoonPage.vue"), meta: { title: "术语管理", icon: "terminology", menu: true, order: 3, disabled: true, badge: "后续版本" } },
			{ path: "/inspection-report", name: "inspection-report", component: () => import("@/pages/placeholder/ComingSoonPage.vue"), meta: { title: "检查报告", icon: "inspectionReport", menu: true, order: 4, disabled: true, badge: "后续版本" } }
		]
	},
	{
		path: "service-access",
		name: "service-access",
		redirect: { name: "api-service" },
		meta: { title: "动作编排", icon: "serviceAccess", menu: true, order: 2 },
		children: [
			{ path: "/api-service", name: "api-service", component: () => import("@/modules/service-integration/ApiServicePage.vue"), meta: { title: "API服务", icon: "apiService", menu: true, order: 1 } },
			{ path: "/mcp-service", name: "mcp-service", component: () => import("@/modules/service-integration/McpServicePage.vue"), meta: { title: "MCP服务", icon: "mcpService", menu: true, order: 2 } },
			{ path: "/function-service", name: "function-service", component: () => import("@/pages/placeholder/ComingSoonPage.vue"), meta: { title: "函数服务", icon: "functionService", menu: true, order: 3, disabled: true, badge: "后续版本" } }
		]
	},
	{
		path: "fact-access",
		name: "fact-access",
		redirect: { name: "object-instance-modeling" },
		meta: { title: "事实孪生", icon: "factAccess", menu: true, order: 3 },
		children: [
			{
				path: "/object-instance-modeling",
				name: "object-instance-modeling",
				component: () => import("@/modules/object-fact/ObjectFactPage.vue"),
				meta: { title: "对象实例建模", icon: "objectInstance", menu: true, order: 1 }
			},
			{ path: "/fact-provenance", name: "fact-provenance", component: () => import("@/pages/placeholder/ComingSoonPage.vue"), meta: { title: "事实溯源", icon: "detail", menu: true, order: 2, disabled: true, badge: "后续版本" } },
			{ path: "/fact-events", name: "fact-events", component: () => import("@/pages/placeholder/ComingSoonPage.vue"), meta: { title: "状态与事件", icon: "ticket", menu: true, order: 3, disabled: true, badge: "后续版本" } },
			{ path: "/fact-history", name: "fact-history", component: () => import("@/pages/placeholder/ComingSoonPage.vue"), meta: { title: "版本与回溯", icon: "audit", menu: true, order: 4, disabled: true, badge: "后续版本" } }
		]
	}
];
