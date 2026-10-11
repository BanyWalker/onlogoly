<template>
	<ElSubMenu v-if="showChildren && item.children.length > 0" :index="item.name" :disabled="item.disabled">
		<template #title>
			<ElIcon v-if="resolveAppIcon(item.icon)"><component :is="resolveAppIcon(item.icon)" /></ElIcon>
			<span>{{ item.label }}</span>
			<ElTag v-if="item.badge" size="small" type="info" effect="plain" class="ctff-nav-badge">{{ item.badge }}</ElTag>
		</template>
		<AppNavigationItem v-for="child in item.children" :key="child.name" :item="child" :show-children="showChildren" />
	</ElSubMenu>
	<ElMenuItem v-else :index="item.name" :disabled="item.disabled">
		<ElIcon v-if="resolveAppIcon(item.icon)"><component :is="resolveAppIcon(item.icon)" /></ElIcon>
		<span>{{ item.label }}</span>
		<ElTag v-if="item.badge" size="small" type="info" effect="plain" class="ctff-nav-badge">{{ item.badge }}</ElTag>
	</ElMenuItem>
</template>

<script setup lang="ts">
import { resolveAppIcon } from "@/navigation/icons";
import type { AppNavigationItem } from "@/navigation/navigation";

defineProps<{
	item: AppNavigationItem;
	showChildren: boolean;
}>();
</script>

<style scoped>
.ctff-nav-badge {
	margin-left: 6px;
	transform: scale(0.85);
}
</style>
