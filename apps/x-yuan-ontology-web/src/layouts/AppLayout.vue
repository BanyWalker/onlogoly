<template>
	<div
		class="ctff-app-shell"
		:class="{ 'is-sidebar-collapsed': effectiveSidebarCollapsed }"
		:data-navigation-mode="navigationMode"
	>
		<aside v-if="navigationMode === 'side'" id="ctff-primary-sidebar" class="ctff-shell-sidebar">
			<div class="ctff-sidebar-brand">
				<div class="ctff-brand-mark">FF</div>
				<strong>{{ appConfig.brandName }}</strong>
			</div>
			<div class="ctff-sidebar-menu">
				<AppNavigation :items="sidebarNavigationItems" :active-name="activeMenu" :collapsed="effectiveSidebarCollapsed" />
			</div>
		</aside>

		<div class="ctff-shell-frame">
			<header class="ctff-shell-topbar" :class="{ 'has-sidebar-toggle': showTopbarSidebarToggle }">
				<div v-if="navigationMode !== 'side'" class="ctff-topbar-brand">
					<div class="ctff-brand-mark">FF</div>
					<strong>{{ appConfig.brandName }}</strong>
				</div>

				<div v-if="showTopbarSidebarToggle" class="ctff-sidebar-toggle-slot ctff-sidebar-toggle-slot--topbar">
					<AppSidebarToggle :collapsed="effectiveSidebarCollapsed" controls="ctff-primary-sidebar" @toggle="toggleSidebar" />
				</div>

				<nav v-if="hasTopNavigation" class="ctff-top-navigation" aria-label="主导航">
					<AppNavigation
						:items="appNavigation"
						:active-name="topNavigationActiveName"
						orientation="horizontal"
						:show-children="navigationMode === 'top'"
					/>
				</nav>

				<div class="ctff-topbar-actions">
					<ElTooltip :content="isDarkTheme ? '切换亮色主题' : '切换暗色主题'" placement="bottom">
						<ElButton
							circle
							text
							class="ctff-topbar-icon-button"
							:aria-label="isDarkTheme ? '切换亮色主题' : '切换暗色主题'"
							@click="toggleTheme"
						>
							<ElIcon><component :is="themeToggleIcon" /></ElIcon>
						</ElButton>
					</ElTooltip>
					<ElButton circle text class="ctff-topbar-icon-button" aria-label="搜索">
						<ElIcon><Search /></ElIcon>
					</ElButton>
					<ElBadge is-dot class="ctff-badge">
						<ElButton circle text class="ctff-topbar-icon-button" aria-label="通知">
							<ElIcon><Bell /></ElIcon>
						</ElButton>
					</ElBadge>
					<div class="ctff-user-card">
						<div class="ctff-user-avatar">A</div>
						<strong>Admin</strong>
						<span class="ctff-user-caret">▼</span>
					</div>
				</div>
			</header>

			<div class="ctff-shell-body ctff-shell-body--split" :class="{ 'is-sidebar-collapsed': effectiveSidebarCollapsed }">
				<aside v-if="navigationMode === 'top-side' && hasSidebar" id="ctff-primary-sidebar" class="ctff-shell-sidebar">
					<div class="ctff-sidebar-menu">
						<AppNavigation :items="sidebarNavigationItems" :active-name="activeMenu" :collapsed="effectiveSidebarCollapsed" />
					</div>
				</aside>

				<div class="ctff-shell-main">
					<div v-if="showTabs" class="ctff-route-tabs-row">
						<div v-if="hasSidebar" class="ctff-sidebar-toggle-slot ctff-sidebar-toggle-slot--tabs">
							<AppSidebarToggle :collapsed="effectiveSidebarCollapsed" controls="ctff-primary-sidebar" @toggle="toggleSidebar" />
						</div>
						<AppRouteTabs :active-tab="activeTab" :tabs="tabs" @change="handleTabChange" @remove="handleTabRemove" />
					</div>
					<main class="ctff-workspace">
						<router-view />
					</main>
				</div>
			</div>
		</div>
	</div>
</template>

<script setup lang="ts">
import { storeToRefs } from "pinia";
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Bell, Moon, Search, Sunny } from "@element-plus/icons-vue";

import { appConfig } from "@/config/appConfig";
import { useCtffTheme } from "@/hooks/useCtffTheme";
import { useMediaQuery } from "@/hooks/useMediaQuery";
import AppNavigation from "@/layouts/components/AppNavigation.vue";
import AppRouteTabs from "@/layouts/components/AppRouteTabs.vue";
import AppSidebarToggle from "@/layouts/components/AppSidebarToggle.vue";
import { resolveActiveMenu } from "@/navigation/navigation";
import { appNavigation } from "@/router/routes";
import { useAppTabsStore } from "@/stores/appTabs";

const router = useRouter();
const route = useRoute();
const navigationMode = appConfig.navigationMode;
const isNarrowViewport = useMediaQuery("(width <= 960px)");
const sidebarCollapsed = ref(isNarrowViewport.value);
const { isDarkTheme, toggleTheme } = useCtffTheme();
const tabsStore = useAppTabsStore();
const { showTabs, tabs } = storeToRefs(tabsStore);

watch(
	() => route.fullPath,
	() => {
		tabsStore.openTab(route);
	},
	{ immediate: true }
);

const activeMenu = computed(() => resolveActiveMenu(route, appNavigation));
const activeTab = computed(() => route.fullPath);
const hasTopNavigation = navigationMode === "top";
const sidebarNavigationItems = computed(() => appNavigation);
const hasSidebar = computed(() => navigationMode === "side" || navigationMode === "top-side");
const effectiveSidebarCollapsed = computed(() => hasSidebar.value && sidebarCollapsed.value);
const showTopbarSidebarToggle = computed(() => hasSidebar.value && !showTabs.value);
const topNavigationActiveName = computed(() => activeMenu.value);
const themeToggleIcon = computed(() => (isDarkTheme.value ? Sunny : Moon));

watch(isNarrowViewport, isNarrow => {
	sidebarCollapsed.value = isNarrow;
});

function toggleSidebar() {
	if (!hasSidebar.value) {
		return;
	}

	sidebarCollapsed.value = !sidebarCollapsed.value;
}

function handleTabChange(tabPath: string | number) {
	if (typeof tabPath !== "string" || tabPath === route.fullPath) {
		return;
	}

	void router.push(tabPath);
}

function handleTabRemove(tabPath: string | number) {
	if (typeof tabPath !== "string") {
		return;
	}

	const nextTabPath = tabsStore.closeTab(tabPath);

	if (tabPath !== route.fullPath || nextTabPath === route.fullPath) {
		return;
	}

	void router.push(nextTabPath);
}
</script>
