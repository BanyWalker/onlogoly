import "vue-router";

import type { AppIconName } from "@/navigation/icons";

declare module "vue-router" {
	interface RouteMeta {
		title?: string;
		icon?: AppIconName;
		order?: number;
		menu?: boolean;
		affix?: boolean;
		activeMenu?: string;
		/** 菜单禁用占位（后续版本启用），TDD-OM-002 §2.4 */
		disabled?: boolean;
		/** 菜单角标文案，如"后续版本" */
		badge?: string;
	}
}
