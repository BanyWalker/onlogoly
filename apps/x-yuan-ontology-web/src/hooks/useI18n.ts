import { computed } from "vue";
import { useI18n as useVueI18n } from "vue-i18n";

const LOCALE_STORAGE_KEY = "x-admin-locale";

export function useI18n() {
	const i18n = useVueI18n();

	const currentLocale = computed(() => i18n.locale.value);

	const setLocale = (locale: string) => {
		i18n.locale.value = locale;
		localStorage.setItem(LOCALE_STORAGE_KEY, locale);
	};

	return {
		...i18n,
		currentLocale,
		setLocale
	};
}

export function getDefaultLocale() {
	return localStorage.getItem(LOCALE_STORAGE_KEY) || "zh-CN";
}
