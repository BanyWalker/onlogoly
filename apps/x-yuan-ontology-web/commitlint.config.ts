import type { UserConfig } from "@commitlint/types";

const validTypes = [
	"build", // 构建系统或者外部依赖的改动
	"ci", // CI配置文件和脚本的改动
	"chore", // 其他不修改源代码的改动
	"feat", // 新功能
	"fix", // 修复bug
	"perf", // 性能优化
	"refactor", // 重构
	"revert", // 回退
	"style", // 代码样式修改
	"test", // 测试代码
	"release", // 发布
	"pre-release", // 预发布
	"deps", // 依赖升级或者变更
	"conf", // 配置文件
	"docs" // 文档
];

/**
 * 类型(范围): 描述
 * feat(core): 新增核心功能
 */

const lintConfig: UserConfig = {
	ignores: [commit => commit.includes("init")],
	extends: ["@commitlint/config-conventional"],
	plugins: [
		{
			rules: {
				"custom-header-check": ({ type, header }) => {
					if (!header || !header.includes(": ")) {
						return [false, "提交信息内容必须以英文冒号和空格开头，如：feat(core): 新增核心功能"];
					}

					if (!type || !validTypes.includes(type)) {
						return [false, `提交信息必须为以下类型开头：[${validTypes.join(", ")}]`];
					}

					return [true];
				}
			}
		}
	],
	rules: {
		"custom-header-check": [2, "always"],
		"body-leading-blank": [2, "always"],
		"footer-leading-blank": [1, "always"],
		"header-max-length": [2, "always", 108],
		"subject-empty": [2, "never"],
		"type-empty": [2, "never"],
		"subject-case": [0]
	},
	useEmoji: true,
	themeColorCode: "",
	scopes: [],
	allowCustomScopes: true,
	allowEmptyScopes: true,
	customScopesAlign: "bottom",
	customScopesAlias: "custom",
	emptyScopesAlias: "empty",
	upperCaseSubject: false,
	allowBreakingChanges: ["feat", "fix"],
	breaklineNumber: 100,
	breaklineChar: "|",
	skipQuestions: [],
	issuePrefixs: [{ value: "closed", name: "closed:   ISSUES has been processed" }],
	customIssuePrefixsAlign: "top",
	emptyIssuePrefixsAlias: "skip",
	customIssuePrefixsAlias: "custom",
	allowCustomIssuePrefixs: true,
	allowEmptyIssuePrefixs: true,
	confirmColorize: true,
	maxHeaderLength: Infinity,
	maxSubjectLength: Infinity,
	minSubjectLength: 0,
	scopeOverrides: undefined,
	defaultBody: "",
	defaultIssues: "",
	defaultScope: "",
	defaultSubject: "",
	prompt: {
		messages: {
			type: "为你当前提交的内容选择一种提交类型：",
			scope: "（可选）注明此更改的范围：",
			customScope: "注明此更改的范围：",
			subject: "写一个简短的、祈使语态的更改描述：\n",
			body: "（可选）提供更长的更改描述。使用“|”换行：\n",
			breaking: "（可选）列出任何重大更改。使用“|”换行：\n",
			footerPrefixsSelect: "（可选）选择此更改影响的ISSUES类型：",
			customFooterPrefixs: "输入ISSUES前缀：",
			footer: "列出此更改影响的任何ISSUES。例如：#31, #34：\n",
			confirmCommit: "你确定要继续进行上述提交吗？"
		}
	}
};

export default lintConfig;
