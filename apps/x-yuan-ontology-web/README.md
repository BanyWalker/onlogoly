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

对象建模画布左上角提供“新建对象”，右上角提供撤销/重做布局。底部居中统一展示对象/关系统计、领域选择与图例，并与右下角的缩放、适应画布和全屏操作垂直居中对齐。领域默认全部选中，以浅色背景轻量表示；点击彩色领域名称切换筛选，再次点击当前领域恢复全部对象。窄画布时统计与图例在可用区域内换行，操作栏保持同行。

文字搜索的上一项/下一项按钮紧凑并排为一组。搜索命中的名称、编码和标识属性文字在画布与结果列表中高亮显示，支持忽略大小写，清空搜索后恢复原样。

对象概要中的“添加新的域”支持填写名称及可选的业务说明，创建成功后自动选入；不再要求填写责任角色。

属性清单采用紧凑单行展示，名称、标识、类型及操作并排，悬浮查看完整信息。数据类型提供文本、数值、布尔、日期时间、引用和枚举六个选项，数值同时支持整数和小数，新增数值属性沿用 `Decimal` 接口类型。已有 `Integer` 属性统一显示为“数值”，输入小数默认值时转为 `Decimal`，仅查看时保留原定义。

属性的实例取值规则保留“必填”勾选：勾选时必须提供有效值，不显示空值含义；未勾选时允许不提供，在“必填”选项后紧跟可选的“空值含义”下拉框，可留空或清除。空值指显式 `null`，与省略字段或空字符串不同；新属性默认未勾选、未声明空值含义。接口继续使用 `required`、`nullable` 字段，必填设置同步基数下限，未勾选时同步为允许空值；后端接受未声明或显式清除的 `nullMeaning`，声明时校验枚举值。

打开对象、切换页签和查看属性不会自动补写属性字段或调整取值规则；保存校验也不改写未编辑的属性。未保存提示仅由实际草稿差异触发，撤销或成功保存后恢复。

对象建模画布按 `A → B` 展示关系。线上标签集中显示关系名称和目标端基数（如 `1..N`），悬浮可查看关系名称及精确范围。关系编辑器的“基数”固定提供 `1..N`（一对多）、`N..1`（多对一）、`1..1`（一对一）、`M..N`（多对多），按“源对象 → 目标对象”选择并同步调整双端上限，保留已有最小数量约束（切换到单个时不超过 1）。

对象详情的关系列表按“出向关系”和“入向关系”分组，各自显示数量：当前对象为源对象时归入出向，为目标对象时归入入向。两组均保留原有关系名称与源对象 → 目标对象方向，点击条目打开关系配置；自关联同时出现在两组，各计一次，空分组显示对应提示。

`src/config/appConfig.ts` 是品牌、首页路由、导航类型和路由页签的统一配置入口。

支持三种导航类型：

- `side`：左右导航，完整菜单位于左侧。
- `top`：上下导航，完整菜单位于顶部。
- `top-side`：T 型导航，顶栏横跨全宽，完整菜单位于顶栏下方左侧。

当前默认采用 `top-side`：顶部保留品牌和用户操作，左侧按“分类 → 实际菜单”展示两级导航。“本体建模”分类包含对象建模、术语管理、规则建模和检查报告，其后是“服务接入”分类，包含 API服务、MCP服务和函数服务，再后是“事实接入”分类，包含对象实例建模。对象建模、API服务、MCP服务和对象实例建模已接入正式页面，术语管理、规则建模、检查报告和函数服务保留禁用及“后续版本”提示。对象实例建模当前提供工作台入口和页面骨架，导入、创建、编辑、连线与一致性校验能力按对象事实需求逐步接入。侧栏支持收起与展开，原有页面 URL 保持不变。

修改 `appConfig.navigationMode` 后由 Vite HMR 直接生效，不需要维护环境变量或测试专用导航配置。生产包使用构建时选定的导航常量，不提供运行时导航切换。

## 核心工程约定

API 服务的新增、编辑入口共用 `src/modules/service-integration/components/ApiEditorDialog.vue`，包含基本信息、请求 Header、入参、出参四个页签。支持 Path 参数自动补全、Header 批量粘贴、JSON/表单/文本结构配置、多状态码响应及响应 Header；嵌套对象和数组使用同一个字段树编辑器。高级 JSON 编辑保留引用、组合结构、命名示例及扩展字段。

Header 固定值通过契约扩展 `x-value-source: fixed` 和 `x-fixed-value` 保存；认证元信息继承服务配置。本次完善接口定义与保存，现有“测试连接”仍沿用原有请求方式，携带参数的调试面板另行实现。

`tests/` 中包含契约兼容和编辑器保存流程的回归用例。使用 Node.js 22.18+（支持直接执行 TypeScript）运行：

```bash
node --test tests/api-contract.test.ts tests/api-editor.test.mjs
```

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
| API、MCP 页面共享样式   | `src/modules/service-integration/service-integration.css` |
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
