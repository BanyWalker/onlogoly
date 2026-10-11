<template>
	<ElTooltip :content="tooltipContent" placement="bottom">
		<span class="ctff-sidebar-toggle-tooltip">
			<button
				type="button"
				class="ctff-sidebar-toggle"
				:aria-controls="controls"
				:aria-expanded="!collapsed"
				:aria-label="tooltipContent"
				@click="emit('toggle')"
				@keydown="handleKeydown"
			>
				<span class="ctff-sidebar-toggle__icon" :class="{ 'is-collapsed': collapsed }" aria-hidden="true" />
			</button>
		</span>
	</ElTooltip>
</template>

<script setup lang="ts">
import { computed } from "vue";

const props = defineProps<{
	collapsed: boolean;
	controls: string;
}>();

const emit = defineEmits<{
	toggle: [];
}>();

const tooltipContent = computed(() => (props.collapsed ? "展开左侧导航" : "收起左侧导航"));

function handleKeydown(event: KeyboardEvent) {
	if (event.key !== "Enter" && event.key !== " ") {
		return;
	}

	event.preventDefault();
	emit("toggle");
}
</script>
