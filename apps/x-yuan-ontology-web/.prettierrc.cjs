module.exports = {
	// 超过最大值换行
	printWidth: 130,
	// 缩进字节数
	tabWidth: 2,
	// 使用制表符而不是空格缩进
	useTabs: true,
	// 结尾使用分号
	semi: true,
	// 使用双引号
	singleQuote: false,
	// 对象属性仅在需要时使用引号
	quoteProps: "as-needed",
	// 对象字面量括号内保留空格
	bracketSpacing: true,
	// 多行结构尾随逗号
	trailingComma: "none",
	// JSX 中使用双引号
	jsxSingleQuote: false,
	// 单参数箭头函数省略括号
	arrowParens: "avoid",
	// 不插入 pragma 注释
	insertPragma: false,
	// 不要求显式 pragma
	requirePragma: false,
	// Markdown 按原样换行
	proseWrap: "preserve",
	// HTML 空白遵循 CSS 默认规则
	htmlWhitespaceSensitivity: "css",
	// 交由环境处理换行符
	endOfLine: "auto",
	// 全量格式化范围
	rangeStart: 0,
	rangeEnd: Infinity,
	// Vue script/style 不额外缩进
	vueIndentScriptAndStyle: false,
	overrides: [
		{
			files: ["*.nvue"],
			options: {
				parser: "vue"
			}
		}
	]
};
