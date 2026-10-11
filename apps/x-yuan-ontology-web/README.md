# x-admin-element-vue3

基于 Vue 3、TypeScript、Vite 和 Element Plus 的后台应用工程模板，提供 CTFF 主题、三种导航结构、应用壳层和常见后台页面参考实现。

## 环境要求

- Node.js `>=20.19.0`
- pnpm `10+`

## 安装与运行

```bash
pnpm install
pnpm run dev
```

默认开发地址：`http://127.0.0.1:5173`

生产构建：

```bash
pnpm run build
```

## 常用命令

- `pnpm run dev`：启动 Vite 开发服务器。
- `pnpm run build`：执行类型检查并生成生产构建。
- `pnpm run build:develop`：使用 development mode 构建。
- `pnpm run build-only`：仅执行 Vite 生产构建。
- `pnpm run preview`：预览生产构建。
- `pnpm run type-check`：执行 `vue-tsc` 类型检查。
- `pnpm run lint`：执行 ESLint 并自动修复。
- `pnpm run lint:stylelint`：执行 Stylelint 并自动修复。
- `pnpm run lint:prettier`：执行 Prettier。

## 技术栈

- Vue 3 + TypeScript
- Vite 8
- Element Plus
- Vue Router
- Pinia
- Vue I18n
- Tailwind CSS v4

## 目录概览

```text
src
|-- assets/css           # CTFF 主题、Element Plus 适配及应用样式
|-- config               # 应用与 Element Plus 运行配置
|-- hooks
|-- layouts              # 应用壳层
|-- locales
|-- navigation           # 导航数据投影
|-- pages                # 页面实现
|-- router               # 路由定义
`-- types
```

## 应用配置

`src/config/appConfig.ts` 是品牌、首页路由、导航类型和路由页签的统一配置入口。

支持三种导航类型：

- `side`：左右导航，完整菜单位于左侧。
- `top`：上下导航，完整菜单位于顶部。
- `top-side`：T 型导航，顶栏横跨全宽，完整菜单位于顶栏下方左侧。

修改 `defaultNavigationMode` 后由 Vite HMR 直接生效，不需要维护环境变量或测试专用导航配置。生产包使用构建时选定的导航常量，不提供运行时导航切换。

## 核心工程约定

- `src/router/modules/*.ts` 维护普通 Vue Router 路由模块。
- `src/router/routes.ts` 导出路由注册和导航派生共同使用的原始层级路由树。
- 可见菜单路由使用 `meta.menu: true`，稳定的路由名称作为菜单身份。
- 隐藏的详情或编辑页面通过 `meta.activeMenu` 指向需要高亮的可见菜单；无效 `activeMenu` 会被导航配置校验拒绝。
- 菜单、导航高亮、页签和面包屑基于同一路由事实源，不维护第二套完整菜单树。
- 导航内核不维护权限规则；未来引入权限时，路由访问和导航可见性必须消费同一授权结果。
- 面包屑位于页面内容顶部，应用壳层不增加独立面包屑栏。
- 未知 URL 由应用 404 路由处理。
- 新页面接入现有路由体系，不增加专用页面注册运行时。

## CTFF 样式结构

| 职责                    | 入口                                  |
| ----------------------- | ------------------------------------- |
| CTFF 亮暗主题和语义变量 | `src/assets/css/ctff-theme/`          |
| Element Plus 主题适配   | `src/assets/css/ctff-element-plus/`   |
| 浏览器和应用根基线      | `src/assets/css/reset.css`            |
| 应用壳层样式            | `src/assets/css/ctff-app.css`         |
| 页面和业务模式          | `src/assets/css/ctff-modules.css`     |
| 应用全局样式入口        | `src/assets/css/index.scss`           |
| Element Plus 运行配置   | `src/config/ctffElementPlusConfig.ts` |

`ctff-theme/` 内部按基础值、通用 Token 和亮暗语义拆分；`ctff-app.css` 统一承载应用壳层变量与结构样式；
`ctff-element-plus/` 仅保留 Element Plus Sass 变量映射、完整主题编译和结构覆盖。两个主题目录均由各自的
`index.scss` 聚合，最终统一接入应用全局样式入口。

精确 Token、组件映射和实现细节由源码维护。

## 验证

提交前至少执行：

```bash
pnpm run lint -- --max-warnings=0
pnpm run type-check
pnpm run build-only
```

样式或格式相关修改还应执行：

```bash
pnpm run lint:stylelint
pnpm run lint:prettier
```

## 已知说明

- 工程使用 `pnpm-lock.yaml` 作为唯一依赖锁文件。
- 根目录 `auto-imports.d.ts` 已由 TypeScript 和 ESLint 加载。
- 生产构建中的 `@vueuse/core` PURE annotation 和 chunk size 提示当前不影响功能。
