/**
 * side: 左右导航，完整菜单位于左侧。
 * top: 上下导航，完整菜单位于顶部。
 * top-side: T 型导航，顶部栏横跨全宽，完整菜单位于顶部栏下方的左侧。
 */
export type NavigationMode = "side" | "top" | "top-side";

export interface AppConfig {
	brandName: string;
	homeRouteName: string;
	navigationMode: NavigationMode;
	showRouteTabs: boolean;
}

/**
 * TDD-OM-002 §2.4：统一顶栏与左侧二级导航菜单框架，一次成型；
 * 首期仅"对象建模"启用（默认选中），其余功能线为禁用占位，后续原位启用、不重构外壳。
 */
export const appConfig: AppConfig = {
	brandName: "曦元本体平台",
	homeRouteName: "object-model",
	navigationMode: "top-side",
	showRouteTabs: false
};
