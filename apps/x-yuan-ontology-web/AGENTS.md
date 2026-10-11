# AGENTS

## Project Overview

- Stack: Vue 3, TypeScript, Vite 8, Element Plus, Pinia, Vue Router, Vue I18n, Tailwind CSS v4
- Package manager: `pnpm`
- Node version: `>=20.19.0`
- Lockfile: `pnpm-lock.yaml`
- Module type: ESM, with `"type": "module"` in `package.json`

## Setup And Run

```bash
pnpm install
pnpm run dev
pnpm run build
```

Common commands:

- `pnpm run dev`: start the local dev server
- `pnpm run build`: run type-check and production build
- `pnpm run build:develop`: build with development mode
- `pnpm run preview`: preview the production bundle
- `pnpm run type-check`: run `vue-tsc`
- `pnpm run lint`: run ESLint with auto-fix
- `pnpm run lint:stylelint`: run Stylelint with auto-fix
- `pnpm run lint:prettier`: run Prettier

## Directory Notes

```text
src
|-- assets/css
|-- config
|-- hooks
|-- layouts
|-- locales
|-- navigation
|-- pages
|-- router
`-- types
```

Additional notes:

- Page code lives in `src/pages`
- Route modules live in `src/router/modules`
- Global types live in `src/types`
- Root-level `auto-imports.d.ts` is already included by TypeScript and ESLint

## Code And Config Conventions

- Keep the only path alias as `@/* -> src/*`
- This repo does not currently use `unplugin-vue-components`
- Do not add `src/components.d.ts` unless the component auto-registration chain is introduced
- Update `.prettierrc.cjs` for Prettier changes
- Update `eslint.config.mjs` for ESLint changes
- Update `.stylelintrc.cjs` for Stylelint changes

## Change Guidelines

- Prefer the smallest necessary change
- Avoid reformatting unrelated business files
- When changing dependencies, scripts, TypeScript config, or Vite config, verify dev, type-check, and build flows together
- If dependencies change, update `pnpm-lock.yaml`
- If scripts or structure change, update `README.md`

## Pre-Commit Checks

Run at least:

```bash
pnpm run lint -- --max-warnings=0
pnpm run type-check
pnpm run build-only
```

If the change touches style rules or formatting config, also run:

```bash
pnpm run lint:stylelint
pnpm run lint:prettier
```

## Known Notes

- Production build may show `@vueuse/core` PURE annotation warnings; they are currently non-blocking
- The build may show chunk size warnings; treat them as optimization work, not functional failures
