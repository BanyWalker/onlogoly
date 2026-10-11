import { createI18n } from "vue-i18n";

import { getDefaultLocale } from "@/hooks/useI18n";

import { messages } from "./messages";

const i18n = createI18n({
	legacy: false,
	locale: getDefaultLocale(),
	fallbackLocale: "zh-CN",
	messages
});

export default i18n;

export { messages };
