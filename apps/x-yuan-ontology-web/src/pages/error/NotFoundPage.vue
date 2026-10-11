<template>
	<section class="ctff-not-found" aria-labelledby="not-found-title">
		<p class="ctff-not-found__code">404</p>
		<h1 id="not-found-title">页面未找到</h1>
		<p>当前地址不存在或已被移除。</p>
		<ElButton type="primary" @click="goHome">返回首页</ElButton>
	</section>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";

import { appConfig } from "@/config/appConfig";

const router = useRouter();

function goHome() {
	void router.push({ name: appConfig.homeRouteName });
}
</script>
