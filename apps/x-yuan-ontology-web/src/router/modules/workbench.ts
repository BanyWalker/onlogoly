import type { RouteRecordRaw } from "vue-router";

/**
 * 本体建模分类及二级菜单路由（TDD-OM-002 §2.4）：
 * 首期仅"对象建模"启用；术语管理/规则建模/检查报告为禁用占位，后续版本原位启用。
 */
export const workbenchRoutes: RouteRecordRaw[] = [
	{
		path: "ontology-modeling",
		name: "ontology-modeling",
		redirect: { name: "object-model" },
		meta: { title: "本体建模", icon: "policy", menu: true, order: 1 },
		children: [
			{
				path: "/object-model",
				name: "object-model",
				component: () => import("@/modules/object-model/ObjectModelPage.vue"),
				meta: { title: "对象建模", icon: "objectModel", menu: true, order: 1 }
			},
			{
				path: "/terminology",
				name: "terminology",
				component: () => import("@/pages/placeholder/ComingSoonPage.vue"),
				meta: { title: "术语管理", icon: "terminology", menu: true, order: 2, disabled: true, badge: "后续版本" }
			},
			{
				path: "/rule-modeling",
				name: "rule-modeling",
				component: () => import("@/pages/placeholder/ComingSoonPage.vue"),
				meta: { title: "规则建模", icon: "ruleModel", menu: true, order: 3, disabled: true, badge: "后续版本" }
			},
			{
				path: "/inspection-report",
				name: "inspection-report",
				component: () => import("@/pages/placeholder/ComingSoonPage.vue"),
				meta: { title: "检查报告", icon: "inspectionReport", menu: true, order: 4, disabled: true, badge: "后续版本" }
			}
		]
	}
];
