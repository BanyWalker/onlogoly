<template>
	<div class="ctff-route-tabs-bar">
		<ElTabs
			:model-value="activeTab"
			type="card"
			class="ctff-route-tabs"
			@tab-change="handleTabChange"
			@tab-remove="handleTabRemove"
		>
			<ElTabPane v-for="tab in tabs" :key="tab.fullPath" :label="tab.title" :name="tab.fullPath" :closable="!tab.affix">
				<template #label>
					<span v-if="isHomeTab(tab)" class="ctff-route-tab__home-content">
						<span class="ctff-route-tab__icon ctff-route-tab__icon--home" aria-hidden="true" />
						<span class="ctff-route-tab__sr-only">{{ tab.title }}</span>
					</span>
					<span v-else class="ctff-route-tab__label" :title="tab.title">{{ tab.title }}</span>
				</template>
			</ElTabPane>
		</ElTabs>
	</div>
</template>

<script setup lang="ts">
import { appConfig } from "@/config/appConfig";
import type { AppRouteTab } from "@/stores/appTabs";

defineProps<{
	activeTab: string;
	tabs: AppRouteTab[];
}>();

const emit = defineEmits<{
	change: [tabPath: string];
	remove: [tabPath: string];
}>();

function isHomeTab(tab: AppRouteTab) {
	return tab.name === appConfig.homeRouteName;
}

function handleTabChange(tabPath: string | number) {
	if (typeof tabPath !== "string") {
		return;
	}

	emit("change", tabPath);
}

function handleTabRemove(tabPath: string | number) {
	if (typeof tabPath !== "string") {
		return;
	}

	emit("remove", tabPath);
}
</script>
