import type { RouteLocationNormalizedLoaded, RouteRecordRaw } from "vue-router";

import type { AppIconName } from "@/navigation/icons";

export interface AppNavigationItem {
	name: string;
	label: string;
	icon?: AppIconName;
	disabled?: boolean;
	badge?: string;
	children: AppNavigationItem[];
}

interface OrderedNavigationItem extends AppNavigationItem {
	order: number;
	declarationOrder: number;
}

function routeName(route: RouteRecordRaw) {
	return typeof route.name === "string" ? route.name : undefined;
}

function routeTitle(route: RouteRecordRaw) {
	return typeof route.meta?.title === "string" && route.meta.title.length > 0 ? route.meta.title : undefined;
}

function buildItems(routes: readonly RouteRecordRaw[]): OrderedNavigationItem[] {
	return routes
		.map((route, declarationOrder): OrderedNavigationItem | undefined => {
			const children = buildItems(route.children ?? []);

			if (route.meta?.menu !== true) {
				return undefined;
			}

			const name = routeName(route);
			const label = routeTitle(route);

			if (!name || !label) {
				throw new Error(`Visible navigation routes require a string name and title: ${route.path}`);
			}

			return {
				name,
				label,
				icon: route.meta.icon,
				disabled: route.meta.disabled === true,
				badge: typeof route.meta.badge === "string" ? route.meta.badge : undefined,
				children,
				order: typeof route.meta.order === "number" ? route.meta.order : Number.MAX_SAFE_INTEGER,
				declarationOrder
			};
		})
		.filter((item): item is OrderedNavigationItem => item !== undefined)
		.sort((left, right) => left.order - right.order || left.declarationOrder - right.declarationOrder);
}

function stripOrdering(item: OrderedNavigationItem): AppNavigationItem {
	return {
		name: item.name,
		label: item.label,
		icon: item.icon,
		disabled: item.disabled,
		badge: item.badge,
		children: item.children.map(child => stripOrdering(child as OrderedNavigationItem))
	};
}

export function buildNavigationTree(routes: readonly RouteRecordRaw[]): AppNavigationItem[] {
	return buildItems(routes).map(stripOrdering);
}

export function flattenNavigation(items: readonly AppNavigationItem[]): AppNavigationItem[] {
	return items.flatMap(item => [item, ...flattenNavigation(item.children)]);
}

export function findNavigationTrail(items: readonly AppNavigationItem[], name: string): AppNavigationItem[] {
	for (const item of items) {
		if (item.name === name) {
			return [item];
		}

		const childTrail = findNavigationTrail(item.children, name);

		if (childTrail.length > 0) {
			return [item, ...childTrail];
		}
	}

	return [];
}

export function resolveActiveMenu(route: RouteLocationNormalizedLoaded, navigation: readonly AppNavigationItem[]) {
	const preferredName = typeof route.meta.activeMenu === "string" ? route.meta.activeMenu : route.name;

	if (typeof preferredName === "string" && findNavigationTrail(navigation, preferredName).length > 0) {
		return preferredName;
	}

	for (const record of [...route.matched].reverse()) {
		if (typeof record.name === "string" && findNavigationTrail(navigation, record.name).length > 0) {
			return record.name;
		}
	}

	return "";
}

export function validateNavigationConfig(routes: readonly RouteRecordRaw[], navigation: readonly AppNavigationItem[]) {
	const routeNames = new Set<string>();
	const visibleNames = new Set(flattenNavigation(navigation).map(item => item.name));
	const errors: string[] = [];

	function visit(records: readonly RouteRecordRaw[]) {
		for (const route of records) {
			const name = routeName(route);

			if (name) {
				if (routeNames.has(name)) {
					errors.push(`Duplicate route name: ${name}`);
				}
				routeNames.add(name);
			}

			if (typeof route.meta?.activeMenu === "string" && !visibleNames.has(route.meta.activeMenu)) {
				errors.push(`Invalid activeMenu on ${name ?? route.path}: ${route.meta.activeMenu}`);
			}

			visit(route.children ?? []);
		}
	}

	visit(routes);

	if (errors.length > 0) {
		throw new Error(errors.join("\n"));
	}
}
