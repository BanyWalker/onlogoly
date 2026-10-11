declare namespace GlobalPatch {
	import type * as ElIcons from "@element-plus/icons-vue";

	type ElIconType = (typeof ElIcons)[keyof typeof ElIcons];

	export interface Menu {
		text: string;
		icon?: ElIconType;
		isHide?: boolean;
		path: string;
		children?: Menu[];
		meta?: Record<string, unknown>;
		permission?: string[];
	}
}

declare module "mammoth" {
	export function convertToHtml(options: { arrayBuffer: ArrayBuffer }): Promise<{ value: string }>;
}
