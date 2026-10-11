import { onBeforeUnmount, ref } from "vue";

export function useMediaQuery(query: string) {
	const matches = ref(false);
	let mediaQuery: MediaQueryList | undefined;

	function updateMatches(event?: MediaQueryListEvent) {
		matches.value = event?.matches ?? mediaQuery?.matches ?? false;
	}

	if (typeof window !== "undefined") {
		mediaQuery = window.matchMedia(query);
		updateMatches();
		mediaQuery.addEventListener("change", updateMatches);
	}

	onBeforeUnmount(() => {
		mediaQuery?.removeEventListener("change", updateMatches);
	});

	return matches;
}
