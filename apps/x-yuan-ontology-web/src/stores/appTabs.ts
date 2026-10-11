import { defineStore } from "pinia";
import { computed, ref } from "vue";
import type { RouteLocationNormalizedLoaded } from "vue-router";

import { appConfig } from "@/config/appConfig";
import { homeRoute } from "@/router/routes";

export interface AppRouteTab {
	fullPath: string;
	path: string;
	title: string;
	name?: string;
	affix: boolean;
}

function createHomeTab(): AppRouteTab {
	return {
		fullPath: homeRoute.path,
		path: homeRoute.path,
		title: homeRoute.title,
		name: homeRoute.name,
		affix: true
	};
}

function resolveRouteTitle(route: RouteLocationNormalizedLoaded) {
	if (typeof route.meta.title === "string" && route.meta.title.length > 0) {
		return route.meta.title;
	}

	if (typeof route.name === "string" && route.name.length > 0) {
		return route.name;
	}

	return route.path;
}

function createTabFromRoute(route: RouteLocationNormalizedLoaded): AppRouteTab {
	return {
		fullPath: route.fullPath,
		path: route.path,
		title: resolveRouteTitle(route),
		name: typeof route.name === "string" ? route.name : undefined,
		affix: route.meta.affix === true || route.name === appConfig.homeRouteName
	};
}

export const useAppTabsStore = defineStore("app-tabs", () => {
	const showTabs = ref(appConfig.showRouteTabs);
	const visitedTabs = ref<AppRouteTab[]>([createHomeTab()]);

	const tabs = computed(() => {
		const affixTabs = visitedTabs.value.filter(tab => tab.affix);
		const normalTabs = visitedTabs.value.filter(tab => !tab.affix);

		return [...affixTabs, ...normalTabs];
	});

	function ensureHomeTab() {
		const homeTab = createHomeTab();
		const existingIndex = visitedTabs.value.findIndex(tab => tab.name === appConfig.homeRouteName);

		if (existingIndex === -1) {
			visitedTabs.value.unshift(homeTab);
			return;
		}

		visitedTabs.value.splice(existingIndex, 1, {
			...visitedTabs.value[existingIndex],
			...homeTab
		});
	}

	function setShowTabs(value: boolean) {
		showTabs.value = value;
	}

	function openTab(route: RouteLocationNormalizedLoaded) {
		const nextTab = createTabFromRoute(route);
		const existingIndex = visitedTabs.value.findIndex(tab => tab.fullPath === nextTab.fullPath);

		ensureHomeTab();

		if (existingIndex >= 0) {
			visitedTabs.value.splice(existingIndex, 1, nextTab);
			return;
		}

		if (nextTab.name === appConfig.homeRouteName) {
			ensureHomeTab();
			return;
		}

		visitedTabs.value.push(nextTab);
	}

	function closeTab(fullPath: string) {
		const currentTabs = tabs.value;
		const targetIndex = currentTabs.findIndex(tab => tab.fullPath === fullPath);

		if (targetIndex === -1) {
			return homeRoute.path;
		}

		const targetTab = currentTabs[targetIndex];

		if (targetTab.affix) {
			return targetTab.fullPath;
		}

		const fallbackTab = currentTabs[targetIndex + 1] ?? currentTabs[targetIndex - 1] ?? createHomeTab();

		visitedTabs.value = visitedTabs.value.filter(tab => tab.fullPath !== fullPath);
		ensureHomeTab();

		return fallbackTab.fullPath;
	}

	return {
		showTabs,
		tabs,
		setShowTabs,
		openTab,
		closeTab
	};
});
