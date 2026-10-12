<template>
	<span><template v-for="part in searchTextParts(text, query)" :key="part.start"><mark v-if="part.matched" class="search-text-match">{{ part.text }}</mark><template v-else>{{ part.text }}</template></template></span>
</template>

<script setup lang="ts">
import { searchTextParts } from "../search-text";

defineProps<{ text: string; query: string }>();
</script>

<style scoped>
.search-text-match {
	font-weight: 700;
	color: #b45309;
	background: #fef3c7;
	border-radius: 2px;
}
</style>
