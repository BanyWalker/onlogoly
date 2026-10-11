import { computed, ref } from "vue";

export type CtffThemeMode = "light" | "dark";

const THEME_STORAGE_KEY = "x-admin-ctff-theme";
const DEFAULT_THEME: CtffThemeMode = "light";

function normalizeTheme(theme: string | null): CtffThemeMode {
	return theme === "dark" ? "dark" : DEFAULT_THEME;
}

function readStoredTheme(): CtffThemeMode {
	try {
		return normalizeTheme(localStorage.getItem(THEME_STORAGE_KEY));
	} catch {
		return DEFAULT_THEME;
	}
}

const currentTheme = ref<CtffThemeMode>(readStoredTheme());

function applyTheme(theme: CtffThemeMode) {
	if (typeof document !== "undefined") {
		document.documentElement.dataset.ctffTheme = theme;
	}

	try {
		localStorage.setItem(THEME_STORAGE_KEY, theme);
	} catch {
		// Ignore storage failures and keep the runtime theme active.
	}
}

export function initializeCtffTheme() {
	applyTheme(currentTheme.value);
}

export function useCtffTheme() {
	const isDarkTheme = computed(() => currentTheme.value === "dark");

	const setTheme = (theme: CtffThemeMode) => {
		currentTheme.value = theme;
		applyTheme(theme);
	};

	const toggleTheme = () => {
		setTheme(isDarkTheme.value ? "light" : "dark");
	};

	return {
		currentTheme,
		isDarkTheme,
		setTheme,
		toggleTheme
	};
}
