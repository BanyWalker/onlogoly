module.exports = {
	extends: [
		'stylelint-config-standard',
		'stylelint-config-html/vue',
		'stylelint-config-standard-scss',
		'stylelint-config-recommended-vue/scss',
		'stylelint-config-recess-order'
	],
	overrides: [
		{
			files: ['**/*.{vue,html}'],
			customSyntax: 'postcss-html'
		}
	],
	rules: {
		'value-keyword-case': null,
		'no-descending-specificity': null,
		'function-url-quotes': 'always',
		'font-family-no-missing-generic-family-keyword': null,
		'property-no-unknown': null,
		'no-empty-source': null,
		'selector-class-pattern': null,
		'custom-property-pattern': null,
		'value-no-vendor-prefix': null,
		'selector-pseudo-class-no-unknown': [
			true,
			{
				ignorePseudoClasses: ['global', 'v-deep', 'deep']
			}
		]
	}
}
