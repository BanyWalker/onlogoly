<template>
	<div class="object-fact-page">
		<section class="fact-page-header">
			<div>
				<div class="fact-eyebrow">事实接入</div>
				<h1>对象实例建模</h1>
				<p>把对象模型中的类型落成可检索、可校验、可维护的实例事实。</p>
			</div>
			<div class="fact-header-meta">
				<ElTag type="info" effect="plain">首期建设中</ElTag>
				<span class="workspace-label">工作空间 · ENV-OM-001</span>
			</div>
		</section>

		<section class="fact-toolbar" aria-label="对象实例建模工具栏">
			<ElButton type="primary" plain disabled>导入实例拓扑</ElButton>
			<ElButton plain disabled>新建实例</ElButton>
			<ElButton text disabled>连线</ElButton>
			<span class="toolbar-divider" aria-hidden="true" />
			<ElInput disabled class="fact-search" placeholder="按业务标识或名称检索" />
			<ElSelect disabled class="fact-filter" placeholder="对象类型">
				<ElOption label="全部对象类型" value="all" />
			</ElSelect>
			<ElSelect disabled class="fact-filter" placeholder="领域">
				<ElOption label="全部领域" value="all" />
			</ElSelect>
		</section>

		<section class="fact-workspace" aria-label="对象实例工作区">
			<aside class="fact-instance-list">
				<div class="panel-title">
					<span>实例清单</span>
					<ElTag size="small" effect="plain">0</ElTag>
				</div>
				<ElEmpty :image-size="64" description="暂无对象实例" />
			</aside>
			<main class="fact-canvas">
				<div class="canvas-grid" aria-hidden="true" />
				<div class="canvas-empty">
					<div class="canvas-empty-icon"><ElIcon><Connection /></ElIcon></div>
					<h2>对象实例拓扑工作区</h2>
					<p>完成后端实例存储与导入校验后，这里将展示实例节点、关系连线和一致性标记。</p>
					<ElButton type="primary" plain disabled>等待接入实例数据</ElButton>
				</div>
			</main>
			<aside class="fact-detail-panel">
				<div class="panel-title">实例详情</div>
				<ElEmpty :image-size="56" description="选择实例查看详情" />
			</aside>
		</section>
	</div>
</template>

<script setup lang="ts">
import { Connection } from "@element-plus/icons-vue";
</script>

<style scoped lang="scss">
.object-fact-page {
	display: flex;
	flex-direction: column;
	gap: 14px;
	height: 100%;
	min-height: 640px;
	padding: 22px 24px 24px;
	box-sizing: border-box;
	background: #f7f9fc;
}

.fact-page-header,
.fact-toolbar,
.fact-instance-list,
.fact-canvas,
.fact-detail-panel {
	border: 1px solid #e5ebf2;
	background: #fff;
	box-shadow: 0 6px 20px rgb(39 73 107 / 4%);
}

.fact-page-header {
	display: flex;
	align-items: flex-start;
	justify-content: space-between;
	padding: 18px 20px;
	border-radius: 12px;
}

.fact-eyebrow {
	margin-bottom: 6px;
	color: #6f83a0;
	font-size: 12px;
	letter-spacing: 0.08em;
}

h1,
h2,
p {
	margin: 0;
}

h1 {
	color: #24344d;
	font-size: 22px;
	font-weight: 650;
}

.fact-page-header p {
	margin-top: 7px;
	color: #71839b;
	font-size: 13px;
}

.fact-header-meta {
	display: flex;
	gap: 12px;
	align-items: center;
	padding-top: 2px;
}

.workspace-label {
	color: #8a9bb0;
	font-size: 12px;
}

.fact-toolbar {
	display: flex;
	gap: 8px;
	align-items: center;
	padding: 10px 12px;
	border-radius: 10px;
}

.toolbar-divider {
	width: 1px;
	height: 22px;
	margin: 0 4px;
	background: #e5ebf2;
}

.fact-search {
	width: 240px;
	margin-left: auto;
}

.fact-filter {
	width: 150px;
}

.fact-workspace {
	display: grid;
	grid-template-columns: 246px minmax(0, 1fr) 300px;
	flex: 1;
	gap: 14px;
	min-height: 0;
}

.fact-instance-list,
.fact-canvas,
.fact-detail-panel {
	position: relative;
	min-height: 420px;
	border-radius: 12px;
	overflow: hidden;
}

.panel-title {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 14px 16px;
	border-bottom: 1px solid #edf1f6;
	color: #34455e;
	font-size: 13px;
	font-weight: 600;
}

.fact-instance-list :deep(.el-empty),
.fact-detail-panel :deep(.el-empty) {
	padding: 70px 12px 20px;
}

.fact-canvas {
	display: grid;
	place-items: center;
	background: #f8fafd;
}

.canvas-grid {
	position: absolute;
	inset: 0;
	background-image: linear-gradient(#e8eef5 1px, transparent 1px), linear-gradient(90deg, #e8eef5 1px, transparent 1px);
	background-size: 24px 24px;
	mask-image: linear-gradient(to bottom, rgb(0 0 0 / 56%), transparent 84%);
}

.canvas-empty {
	position: relative;
	max-width: 380px;
	padding: 30px;
	border: 1px solid rgb(215 226 238 / 90%);
	border-radius: 16px;
	background: rgb(255 255 255 / 88%);
	box-shadow: 0 12px 30px rgb(40 78 112 / 8%);
	text-align: center;
}

.canvas-empty-icon {
	display: grid;
	place-items: center;
	width: 42px;
	height: 42px;
	margin: 0 auto 12px;
	border-radius: 12px;
	background: #eaf4fb;
	color: #2d86bd;
	font-size: 22px;
}

.canvas-empty h2 {
	color: #33465f;
	font-size: 16px;
	font-weight: 600;
}

.canvas-empty p {
	margin: 9px 0 18px;
	color: #7d8da2;
	font-size: 12px;
	line-height: 1.7;
}

@media (max-width: 1180px) {
	.fact-workspace {
		grid-template-columns: 220px minmax(0, 1fr);
	}

	.fact-detail-panel {
		display: none;
	}
}
</style>
