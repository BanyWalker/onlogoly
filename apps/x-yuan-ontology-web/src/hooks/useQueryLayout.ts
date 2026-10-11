import { computed, nextTick, onBeforeUnmount, ref, watch, type CSSProperties } from "vue";
import type { FormInstance } from "element-plus";

// Runtime layout for the two bundled query examples with their existing short labels.
export function useQueryLayout(labels: readonly string[]) {
	const queryForm = ref<FormInstance>();
	const columns = ref(1);
	const expanded = ref(false);
	const labelWidths = ref<number[]>([]);
	const actionFit = ref<"control-start" | "control-end" | "wrap">("control-start");
	const overflow = computed(() => labels.length > columns.value * 2);
	const visibleCount = computed(() => (overflow.value && !expanded.value ? columns.value * 2 : labels.length));
	const action = computed(() => {
		const n = columns.value;
		const f = visibleCount.value;
		const rows = Math.ceil(f / n);
		const immediate = rows === 1 && f < n;
		const full = f % n === 0;
		return {
			row: full ? rows + 1 : rows,
			col: immediate ? f + 1 : n,
			immediate,
			mode:
				rows === 1
					? immediate
						? "single_row_immediate_following"
						: "single_row_full_next_last_column"
					: full
						? "multi_row_full_next_last_column"
						: "multi_row_last_column"
		};
	});
	const tracks = computed(() => {
		const widths = Array<number>(columns.value).fill(0);
		labelWidths.value.slice(0, visibleCount.value).forEach((width, index) => {
			const col = index % columns.value;
			widths[col] = Math.max(widths[col] ?? 0, width);
		});
		return widths;
	});
	function labelWidth(index: number) {
		// Element Plus includes the 12px right padding in label-width.
		return `${(tracks.value[index % columns.value] ?? 0) + 12}px`;
	}
	const formStyle = computed<CSSProperties>(() => ({ "--ctff-query-column-count": columns.value }));
	const actionStyle = computed<CSSProperties>(() => ({
		"--ctff-query-action-row": action.value.row,
		"--ctff-query-action-col": action.value.col,
		"--ctff-query-label-width":
			action.value.immediate || actionFit.value !== "control-start" ? "0px" : labelWidth(action.value.col - 1),
		"--ctff-query-action-justify": actionFit.value === "control-end" ? "flex-end" : "flex-start",
		"--ctff-query-action-wrap": actionFit.value === "wrap" ? "wrap" : "nowrap"
	}));
	let observer: ResizeObserver | undefined;
	let currentElement: HTMLElement | undefined;
	let disposed = false;
	let frame: number | undefined;
	function scheduleMeasure() {
		if (frame !== undefined) return;
		frame = requestAnimationFrame(() => {
			frame = undefined;
			if (!disposed) void measure();
		});
	}
	function contentWidth(element: HTMLElement) {
		const style = getComputedStyle(element);
		return (
			parseFloat(style.width) -
			(style.boxSizing === "border-box"
				? parseFloat(style.paddingLeft) +
					parseFloat(style.paddingRight) +
					parseFloat(style.borderLeftWidth) +
					parseFloat(style.borderRightWidth)
				: 0)
		);
	}
	async function measure() {
		const element = currentElement;
		if (!element || element.getClientRects().length === 0) return;
		const width = contentWidth(element);
		if (width <= 0) return;
		columns.value = Math.max(1, Math.floor(width / 340));
		const label = element.querySelector<HTMLElement>(".el-form-item__label");
		if (!label) return;
		const labelStyle = getComputedStyle(label);
		const context = document.createElement("canvas").getContext("2d");
		if (!context) return;
		context.font = labelStyle.font || `${labelStyle.fontWeight} ${labelStyle.fontSize} ${labelStyle.fontFamily}`;
		const spacing = parseFloat(labelStyle.letterSpacing) || 0;
		labelWidths.value = labels.map(text => Math.ceil(context.measureText(text).width + [...text].length * spacing));
		await nextTick();
		if (disposed || currentElement !== element) return;
		const slot = element.querySelector<HTMLElement>(".ctff-query-actions");
		const cluster = slot?.querySelector<HTMLElement>(".ctff-query-action-cluster");
		if (!slot || !cluster) return;
		const slotWidth = contentWidth(slot);
		if (slotWidth <= 0) return;
		// Measure the unwrapped group, including real button sizes and group gaps.
		// Restore styles synchronously so the measuring layout is never painted.
		const previousStyle = cluster.getAttribute("style");
		let actionWidth: number;
		try {
			cluster.style.width = "max-content";
			cluster.style.maxWidth = "none";
			cluster.style.flexWrap = "nowrap";
			actionWidth = cluster.getBoundingClientRect().width;
		} finally {
			if (previousStyle === null) cluster.removeAttribute("style");
			else cluster.setAttribute("style", previousStyle);
		}
		const controlWidth = slotWidth - (action.value.immediate ? 0 : parseFloat(labelWidth(action.value.col - 1)));
		actionFit.value = actionWidth <= controlWidth ? "control-start" : actionWidth <= slotWidth ? "control-end" : "wrap";
	}
	watch(
		() => queryForm.value?.$el as HTMLElement | undefined,
		element => {
			observer?.disconnect();
			if (frame !== undefined) cancelAnimationFrame(frame);
			frame = undefined;
			currentElement = element;
			if (!element) return;
			observer = new ResizeObserver(scheduleMeasure);
			observer.observe(element);
			const slot = element.querySelector<HTMLElement>(".ctff-query-actions");
			if (slot) observer.observe(slot);
			element.querySelectorAll(".ctff-query-action-cluster > *").forEach(child => observer?.observe(child));
			void measure();
			void document.fonts.ready.then(() => {
				if (!disposed && currentElement === element) void measure();
			});
		},
		{ flush: "post" }
	);
	watch(expanded, scheduleMeasure, { flush: "post" });
	onBeforeUnmount(() => {
		disposed = true;
		observer?.disconnect();
		if (frame !== undefined) cancelAnimationFrame(frame);
		currentElement = undefined;
	});
	return { queryForm, expanded, overflow, visibleCount, action, formStyle, actionStyle, labelWidth };
}
