<template>
	<ElSubMenu v-if="props.showChildren && props.item.children.length > 0" :index="props.item.name" :disabled="props.item.disabled">
		<template #title>
			<ElIcon v-if="resolveAppIcon(props.item.icon)"><component :is="resolveAppIcon(props.item.icon)" /></ElIcon>
			<span>{{ props.item.label }}</span>
			<ElTag v-if="props.showBadge && props.item.badge" size="small" type="info" effect="plain" class="ctff-nav-badge">{{
				props.item.badge
			}}</ElTag>
		</template>
		<AppNavigationItem
			v-for="child in props.item.children"
			:key="child.name"
			:item="child"
			:show-children="props.showChildren"
			:show-badge="props.showBadge"
		/>
	</ElSubMenu>
	<ElMenuItem v-else :index="props.item.name" :disabled="props.item.disabled">
		<ElIcon v-if="resolveAppIcon(props.item.icon)"><component :is="resolveAppIcon(props.item.icon)" /></ElIcon>
		<span>{{ props.item.label }}</span>
		<ElTag v-if="props.showBadge && props.item.badge" size="small" type="info" effect="plain" class="ctff-nav-badge">{{
			props.item.badge
		}}</ElTag>
	</ElMenuItem>
</template>

<script setup lang="ts">
import { resolveAppIcon } from "@/navigation/icons";
import type { AppNavigationItem } from "@/navigation/navigation";

const props = withDefaults(
	defineProps<{
		item: AppNavigationItem;
		showChildren: boolean;
		showBadge?: boolean;
	}>(),
	{
		showBadge: true
	}
);
</script>

<style scoped>
.ctff-nav-badge {
	margin-left: 6px;
	transform: scale(0.85);
}
</style>
