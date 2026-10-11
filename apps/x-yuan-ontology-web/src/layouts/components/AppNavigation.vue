<template>
	<ElMenu
		:default-active="activeName"
		:mode="orientation"
		:collapse="orientation === 'vertical' && collapsed"
		:ellipsis="orientation === 'horizontal'"
		:default-openeds="defaultOpeneds"
		class="ctff-nav-menu"
		unique-opened
		@select="handleSelect"
	>
		<AppNavigationItem v-for="item in items" :key="item.name" :item="item" :show-children="showChildren" />
	</ElMenu>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";
import { computed } from "vue";

import AppNavigationItem from "@/layouts/components/AppNavigationItem.vue";
import { findNavigationTrail, type AppNavigationItem as NavigationItem } from "@/navigation/navigation";

const props = withDefaults(
	defineProps<{
		items: NavigationItem[];
		activeName: string;
		orientation?: "horizontal" | "vertical";
		collapsed?: boolean;
		showChildren?: boolean;
	}>(),
	{
		orientation: "vertical",
		collapsed: false,
		showChildren: true
	}
);

const router = useRouter();
const defaultOpeneds = computed(() =>
	findNavigationTrail(props.items, props.activeName)
		.slice(0, -1)
		.map(item => item.name)
);

function handleSelect(name: string) {
	void router.push({ name });
}
</script>
