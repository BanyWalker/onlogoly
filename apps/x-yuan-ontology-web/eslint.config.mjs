import eslintJs from "@eslint/js";
import eslintConfigPrettier from "eslint-config-prettier";
import eslintPluginVue from "eslint-plugin-vue";
import globals from "globals";
import typescriptEslint from "typescript-eslint";

export default typescriptEslint.config(
	{
		ignores: ["public/*", "**/node_modules", "**/dist", "**/*.md", "auto-imports.d.ts"]
	},
	{
		files: ["**/*.{ts,vue}"],
		extends: [
			eslintJs.configs.recommended,
			...typescriptEslint.configs.recommended,
			...eslintPluginVue.configs["flat/recommended"]
		],
		languageOptions: {
			ecmaVersion: "latest",
			sourceType: "module",
			globals: {
				...globals.browser,
				...globals.node
			},
			parserOptions: {
				parser: typescriptEslint.parser
			}
		},
		rules: {
			"no-undef": "off",
			"no-console": "off",
			"vue/multi-word-component-names": "off",
			"@typescript-eslint/no-unused-vars": [
				"error",
				{
					argsIgnorePattern: "^_"
				}
			]
		}
	},
	eslintConfigPrettier
);
