export const ctffElementPlusRuntimeConfig = {
	namespace: "el",
	size: "default",
	zIndex: 1000,
	button: {
		autoInsertSpace: false
	},
	link: {
		underline: "hover"
	},
	message: {
		grouping: true,
		showClose: true,
		offset: 16
	},
	table: {
		showOverflowTooltip: true,
		tooltipEffect: "light"
	}
} as const;

export const ctffElementPlusInstallOptions = {
	size: ctffElementPlusRuntimeConfig.size,
	zIndex: ctffElementPlusRuntimeConfig.zIndex,
	namespace: ctffElementPlusRuntimeConfig.namespace
} as const;

/**
 * CTFF 密度预设当前仅作为参考数据保留。
 *
 * 现阶段主题默认值的实际来源是 CSS token / 主题链路：
 * ctff-theme/tokens.scss -> theme.scss / theme-overrides.scss / ctff-app.css
 *
 * 由于当前应用还不支持运行时密度切换，下面的 default 预设
 * 不是默认态的实际生效来源。
 */
export type CtffElementPlusDensity = "compact" | "default" | "relaxed";

export const ctffElementPlusDensityVars: Record<CtffElementPlusDensity, Record<string, string>> = {
	compact: {
		// 可桥接到 Element Plus 公共 CSS 变量
		"--ctff-control-height-sm": "24px",
		"--ctff-control-height-default": "28px",
		"--ctff-control-height-lg": "36px",
		"--ctff-pagination-button-size": "24px",
		"--ctff-pagination-button-size-sm": "24px",
		"--ctff-pagination-button-size-lg": "28px",
		"--ctff-pagination-item-gap": "4px",
		"--ctff-menu-item-height": "36px",
		"--ctff-tabs-header-height": "36px",
		"--ctff-upload-dragger-padding-x": "16px",
		"--ctff-upload-dragger-padding-y": "16px",
		"--ctff-timeline-node-size": "8px",

		// 需由 CTFF 自身样式层控制
		"--ctff-table-cell-height": "34px",
		"--ctff-table-cell-height-default": "34px",
		"--ctff-form-item-margin-bottom": "16px",
		"--ctff-description-item-line-height": "24px",
		"--ctff-description-row-gap": "8px",
		"--ctff-description-column-gap": "48px",
		"--ctff-description-label-gap": "8px",
		"--ctff-section-panel-header-height": "36px",
		"--ctff-section-panel-header-padding-x": "12px",
		"--ctff-section-panel-body-padding-x": "24px",
		"--ctff-section-panel-body-padding-y": "24px",
		"--ctff-section-panel-gap": "12px",
		"--ctff-dropdown-item-height": "28px",
		"--ctff-select-option-height": "28px",
		"--ctff-picker-cell-size": "28px",
		"--ctff-input-number-control-width": "28px",
		"--ctff-progress-height": "6px",
		"--ctff-steps-node-size": "20px",
		"--ctff-query-grid-row-gap": "12px",
		"--ctff-query-field-label-control-gap": "8px",
		"--ctff-query-action-gap": "8px",
		"--ctff-query-action-cluster-gap": "12px",
		"--ctff-module-gap": "12px",
		"--ctff-query-surface-padding": "12px",
		"--ctff-result-gap": "12px",
		"--ctff-result-tail-gap": "12px"
	},
	/**
	 * 对应当前 CTFF 的基线规范。
	 * 默认值应优先维护在 CSS tokens 中。
	 *
	 * 下面按职责分两类：
	 * 1. 可通过 theme.scss 桥接到 Element Plus 公共 CSS 变量
	 * 2. 仍需由 CTFF 自身样式层控制（theme-overrides.scss / ctff-app.css）
	 */
	default: {
		// 可桥接到 Element Plus 公共 CSS 变量
		"--ctff-control-height-sm": "24px",
		"--ctff-control-height-default": "32px",
		"--ctff-control-height-lg": "40px",
		"--ctff-pagination-button-size": "28px",
		"--ctff-pagination-button-size-sm": "24px",
		"--ctff-pagination-button-size-lg": "32px",
		"--ctff-pagination-item-gap": "8px",
		"--ctff-menu-item-height": "40px",
		"--ctff-tabs-header-height": "40px",
		"--ctff-upload-dragger-padding-x": "24px",
		"--ctff-upload-dragger-padding-y": "24px",
		"--ctff-timeline-node-size": "10px",

		// 需由 CTFF 自身样式层控制
		"--ctff-table-cell-height": "38px",
		"--ctff-table-cell-height-default": "38px",
		"--ctff-form-item-margin-bottom": "20px",
		"--ctff-description-item-line-height": "24px",
		"--ctff-description-row-gap": "8px",
		"--ctff-description-column-gap": "48px",
		"--ctff-description-label-gap": "8px",
		"--ctff-section-panel-header-height": "39px",
		"--ctff-section-panel-header-padding-x": "16px",
		"--ctff-section-panel-body-padding-x": "24px",
		"--ctff-section-panel-body-padding-y": "24px",
		"--ctff-section-panel-gap": "16px",
		"--ctff-dropdown-item-height": "32px",
		"--ctff-select-option-height": "32px",
		"--ctff-picker-cell-size": "32px",
		"--ctff-input-number-control-width": "32px",
		"--ctff-progress-height": "8px",
		"--ctff-steps-node-size": "24px",
		"--ctff-query-grid-row-gap": "16px",
		"--ctff-query-field-label-control-gap": "12px",
		"--ctff-query-action-gap": "8px",
		"--ctff-query-action-cluster-gap": "16px",
		"--ctff-module-gap": "16px",
		"--ctff-query-surface-padding": "16px",
		"--ctff-result-gap": "16px",
		"--ctff-result-tail-gap": "16px"
	},
	relaxed: {
		// 可桥接到 Element Plus 公共 CSS 变量
		"--ctff-control-height-sm": "28px",
		"--ctff-control-height-default": "36px",
		"--ctff-control-height-lg": "44px",
		"--ctff-pagination-button-size": "32px",
		"--ctff-pagination-button-size-sm": "28px",
		"--ctff-pagination-button-size-lg": "36px",
		"--ctff-pagination-item-gap": "12px",
		"--ctff-menu-item-height": "44px",
		"--ctff-tabs-header-height": "44px",
		"--ctff-upload-dragger-padding-x": "24px",
		"--ctff-upload-dragger-padding-y": "24px",
		"--ctff-timeline-node-size": "12px",

		// 需由 CTFF 自身样式层控制
		"--ctff-table-cell-height": "44px",
		"--ctff-table-cell-height-default": "44px",
		"--ctff-form-item-margin-bottom": "24px",
		"--ctff-description-item-line-height": "24px",
		"--ctff-description-row-gap": "8px",
		"--ctff-description-column-gap": "48px",
		"--ctff-description-label-gap": "8px",
		"--ctff-section-panel-header-height": "44px",
		"--ctff-section-panel-header-padding-x": "20px",
		"--ctff-section-panel-body-padding-x": "24px",
		"--ctff-section-panel-body-padding-y": "24px",
		"--ctff-section-panel-gap": "20px",
		"--ctff-dropdown-item-height": "36px",
		"--ctff-select-option-height": "36px",
		"--ctff-picker-cell-size": "36px",
		"--ctff-input-number-control-width": "36px",
		"--ctff-progress-height": "10px",
		"--ctff-steps-node-size": "28px",
		"--ctff-query-grid-row-gap": "20px",
		"--ctff-query-field-label-control-gap": "12px",
		"--ctff-query-action-gap": "12px",
		"--ctff-query-action-cluster-gap": "20px",
		"--ctff-module-gap": "20px",
		"--ctff-query-surface-padding": "20px",
		"--ctff-result-gap": "20px",
		"--ctff-result-tail-gap": "20px"
	}
};
