<template>
	<ElConfigProvider
		:locale="elementLocale"
		:button="ctffElementPlusRuntimeConfig.button"
		:link="ctffElementPlusRuntimeConfig.link"
		:message="ctffElementPlusRuntimeConfig.message"
		:table="ctffElementPlusRuntimeConfig.table"
	>
		<router-view />
	</ElConfigProvider>
</template>

<script setup lang="ts">
import { computed } from "vue";
import en from "element-plus/es/locale/lang/en";
import zhCn from "element-plus/es/locale/lang/zh-cn";

import { ctffElementPlusRuntimeConfig } from "@/config/ctffElementPlusConfig";
import { useI18n } from "@/hooks/useI18n";

const { currentLocale } = useI18n();

const elementLocale = computed(() => (currentLocale.value === "zh-CN" ? zhCn : en));
</script>
