import type { RouteRecordRaw } from "vue-router";

import { appConfig } from "@/config/appConfig";
import { buildNavigationTree, validateNavigationConfig } from "@/navigation/navigation";

import { workbenchRoutes } from "./modules/workbench";

export const appRouteChildren: RouteRecordRaw[] = [...workbenchRoutes];

export const appNavigation = buildNavigationTree(appRouteChildren);

validateNavigationConfig(appRouteChildren, appNavigation);

function joinRoutePath(parentPath: string, routePath: string) {
	if (routePath.startsWith("/")) {
		return routePath;
	}

	return `/${[parentPath, routePath].join("/").split("/").filter(Boolean).join("/")}`;
}

function findRouteByName(
	routes: readonly RouteRecordRaw[],
	name: string,
	parentPath = ""
): { name: string; path: string; title: string } | undefined {
	for (const route of routes) {
		const path = joinRoutePath(parentPath, route.path);

		if (route.name === name && typeof route.meta?.title === "string") {
			return { name, path, title: route.meta.title };
		}

		const child = findRouteByName(route.children ?? [], name, path);

		if (child) {
			return child;
		}
	}

	return undefined;
}

const configuredHomeRoute = findRouteByName(appRouteChildren, appConfig.homeRouteName);

if (!configuredHomeRoute) {
	throw new Error(`Configured home route does not exist or has no title: ${appConfig.homeRouteName}`);
}

export const homeRoute = configuredHomeRoute;

export const notFoundRoute: RouteRecordRaw = {
	path: "/:pathMatch(.*)*",
	name: "not-found",
	component: () => import("@/pages/error/NotFoundPage.vue"),
	meta: { title: "页面未找到" }
};
