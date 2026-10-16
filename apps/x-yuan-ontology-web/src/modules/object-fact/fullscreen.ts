import { computed, inject, nextTick, onBeforeUnmount, onMounted, provide, ref, type InjectionKey, type Ref } from "vue";

const overlayKey: InjectionKey<Ref<HTMLElement | undefined>> = Symbol("fact-overlay-target");

export function useFactOverlay() {
	return inject(overlayKey, ref<HTMLElement>());
}

export function useCanvasFullscreen(host: Ref<HTMLElement | undefined>, resize: () => void) {
	const isFullscreen = ref(false);
	const overlayTarget = computed(() => (isFullscreen.value ? host.value : undefined));
	provide(overlayKey, overlayTarget);
	let frame: number | undefined;
	let disposed = false;

	async function sync() {
		isFullscreen.value = document.fullscreenElement === host.value;
		await nextTick();
		if (disposed) return;
		if (frame !== undefined) cancelAnimationFrame(frame);
		frame = requestAnimationFrame(() => {
			frame = undefined;
			resize();
		});
	}

	async function toggleFullscreen() {
		if (!host.value) return;
		if (document.fullscreenElement === host.value) await document.exitFullscreen();
		else await host.value.requestFullscreen();
	}

	onMounted(() => {
		document.addEventListener("fullscreenchange", sync);
	});
	onBeforeUnmount(() => {
		disposed = true;
		document.removeEventListener("fullscreenchange", sync);
		if (frame !== undefined) cancelAnimationFrame(frame);
		if (document.fullscreenElement === host.value) void document.exitFullscreen().catch(() => {});
	});

	return { isFullscreen, overlayTarget, toggleFullscreen };
}
