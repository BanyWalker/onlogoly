<template>
	<ElMenu
		:default-active="activeName"
		:mode="orientation"
		:collapse="orientation === 'vertical' && collapsed"
		:ellipsis="orientation === 'horizontal'"
		:default-openeds="defaultOpeneds"
		class="ctff-nav-menu"
		:class="{ 'is-static-categories': staticCategories }"
		unique-opened
		@select="handleSelect"
	>
		<template v-if="staticCategories">
			<template v-for="item in items" :key="item.name">
				<div class="ctff-nav-category" role="heading" aria-level="1">{{ item.label }}</div>
				<AppNavigationItem
					v-for="child in item.children"
					:key="child.name"
					:item="child"
					:show-children="false"
					:show-badge="false"
				/>
			</template>
		</template>
		<template v-else>
			<AppNavigationItem v-for="item in items" :key="item.name" :item="item" :show-children="showChildren" />
		</template>
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
		staticCategories?: boolean;
	}>(),
	{
		orientation: "vertical",
		collapsed: false,
		showChildren: true,
		staticCategories: false
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
