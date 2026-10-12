<template>
	<div ref="containerRef" class="object-canvas" />
</template>

<script setup lang="ts">
import { Graph } from "@antv/x6";
import { onBeforeUnmount, onMounted, ref, watch } from "vue";

import { cardinalityText, relationTargetIds, type GraphObject, type GraphRelation } from "../types";
import { searchTextParts } from "../search-text";

const NODE_WIDTH = 250;
const NODE_HEIGHT = 112;
const MIN_ROUTE_GAP = 24;
const GRAPH_PADDING = { top: 56, right: 56, bottom: 56, left: 56 };

const props = defineProps<{
	objects: GraphObject[];
	relations: GraphRelation[];
	domainColorMap: Map<string, string>;
	domainNameMap: Map<string, string>;
	highlightedIds: Set<string>;
	searchText?: string;
	selectedObjectIds?: string[];
	selectedObjectId?: string;
	selectedRelationId?: string;
}>();

const emit = defineEmits<{
	(e: "select-object", id: string): void;
	(e: "select-relation", id: string): void;
	(e: "create-relation", sourceType: string, targetType: string): void;
	(e: "nodes-moved", positions: Record<string, Point>): void;
	(e: "selection-change", ids: string[]): void;
	(e: "zoom-change", zoom: number): void;
	(e: "canvas-click"): void;
}>();

const containerRef = ref<HTMLDivElement>();
let graph: Graph | null = null;
let resizeObserver: ResizeObserver | null = null;
let centeredOnce = false;
let movingNodeId: string | null = null;
let redrawPending = false;
let readingModelPositions = false;
let dragState: {
	anchorId: string;
	startPositions: Map<string, Point>;
	moved: boolean;
} | null = null;
let applyingGroupMove = false;

function uniqueObjects() {
	const byId = new Map<string, GraphObject>();
	for (const object of props.objects) {
		if (!byId.has(object.id)) {
			byId.set(object.id, object);
		}
	}
	return [...byId.values()];
}

function domainColor(domainId: string) {
	return props.domainColorMap.get(domainId) ?? "#4f7df3";
}

function domainColorOfObject(objectId: string) {
	const object = uniqueObjects().find(item => item.id === objectId);
	return object ? domainColor(object.domainId) : "#4f7df3";
}

function rgba(color: string, alpha: number) {
	const value = color.replace("#", "");
	if (value.length === 6) {
		const red = Number.parseInt(value.slice(0, 2), 16);
		const green = Number.parseInt(value.slice(2, 4), 16);
		const blue = Number.parseInt(value.slice(4, 6), 16);
		return `rgba(${red}, ${green}, ${blue}, ${alpha})`;
	}
	return color;
}

type Point = { x: number; y: number };

function positionOf(objectId: string): Point {
	const cell = graph?.getCellById(objectId);
	if (!readingModelPositions && cell?.isNode()) {
		return cell.getPosition();
	}
	return uniqueObjects().find(object => object.id === objectId)?.layout ?? { x: 100, y: 100 };
}

function portPair(sourceId: string, targetId: string) {
	if (sourceId === targetId) {
		return { source: "p-right", target: "p-bottom" };
	}
	const sourcePosition = positionOf(sourceId);
	const targetPosition = positionOf(targetId);
	const dx = targetPosition.x - sourcePosition.x;
	const dy = targetPosition.y - sourcePosition.y;
	const horizontalGap = Math.abs(dx) - NODE_WIDTH;
	const verticalGap = Math.abs(dy) - NODE_HEIGHT;
	const horizontal = horizontalGap >= MIN_ROUTE_GAP
		? verticalGap < MIN_ROUTE_GAP || horizontalGap >= verticalGap
		: verticalGap < MIN_ROUTE_GAP && Math.abs(dx) / NODE_WIDTH >= Math.abs(dy) / NODE_HEIGHT;
	if (horizontal) {
		return dx >= 0
			? { source: "p-right", target: "p-left" }
			: { source: "p-left", target: "p-right" };
	}
	return dy >= 0
		? { source: "p-bottom", target: "p-top" }
		: { source: "p-top", target: "p-bottom" };
}

function directionOfPort(port: string) {
	switch (port) {
		case "p-left":
			return "left";
		case "p-top":
			return "top";
		case "p-bottom":
			return "bottom";
		default:
			return "right";
	}
}

function selfLoopVertices(objectId: string, sourceY: number, targetX: number) {
	const position = positionOf(objectId);
	const right = position.x + NODE_WIDTH + 48;
	const bottom = position.y + NODE_HEIGHT + 40;
	return [
		{ x: right, y: position.y + sourceY },
		{ x: right, y: bottom },
		{ x: position.x + targetX, y: bottom }
	];
}

function selectedIds() {
	return new Set([
		...(props.selectedObjectIds ?? []),
		...(props.selectedObjectId ? [props.selectedObjectId] : [])
	]);
}

function selectedRelationObjectIds() {
	const ids = new Set<string>();
	if (!props.selectedRelationId) return ids;
	const relation = props.relations.find(item => item.id === props.selectedRelationId);
	if (!relation) return ids;
	ids.add(relation.sourceType);
	for (const target of relationTargetIds(relation)) ids.add(target);
	return ids;
}

function relationTouches(ids: Set<string>, relation: GraphRelation) {
	return ids.has(relation.sourceType) || relationTargetIds(relation).some(target => ids.has(target));
}

function nodeIsFocused(objectId: string) {
	if (props.highlightedIds.has(objectId)) return true;
	if (selectedRelationObjectIds().has(objectId)) return true;
	const selected = selectedIds();
	if (selected.has(objectId)) return true;
	return selected.size > 0 && props.relations.some(relation =>
		relationTouches(selected, relation) && (relation.sourceType === objectId || relationTargetIds(relation).includes(objectId))
	);
}

function hasFocus() {
	return props.highlightedIds.size > 0 || selectedIds().size > 0 || !!props.selectedRelationId;
}

function searchAnnotations(text: string) {
	return JSON.stringify(searchTextParts(text, props.searchText ?? "").filter(part => part.matched).map(part => ({
		start: part.start,
		end: part.start + part.text.length,
		attrs: {
			class: "search-text-match",
			fill: "#b45309",
			stroke: "#fef3c7",
			"stroke-width": 3,
			"stroke-linejoin": "round",
			"paint-order": "stroke",
			"font-weight": 700
		}
	})));
}

function buildNode(object: GraphObject, connections: ConnectionPlan[]) {
	const color = domainColor(object.domainId);
	const domainName = props.domainNameMap.get(object.domainId) ?? object.domainId;
	const searchMatch = props.highlightedIds.has(object.id);
	const selected = selectedIds().has(object.id);
	const highlighted = selected || searchMatch || props.selectedObjectId === object.id;
	const dimmed = hasFocus() && !nodeIsFocused(object.id);
	const nodeOpacity = dimmed ? 0.24 : object.status === "deprecated" ? 0.45 : 1;
	const codeText = object.id;
	return {
		id: object.id,
		shape: "object-node",
		x: object.layout?.x ?? 100,
		y: object.layout?.y ?? 100,
		width: NODE_WIDTH,
		height: NODE_HEIGHT,
		z: 10,
		attrs: {
			body: {
				stroke: highlighted ? color : "#E5EAF2",
				strokeWidth: highlighted ? 1.5 : 1,
				opacity: nodeOpacity
			},
			domainChipBg: { fill: rgba(color, 0.1), opacity: dimmed ? 0.2 : 1 },
			domainDot: { fill: color, opacity: dimmed ? 0.3 : 1 },
			domainLabel: { text: domainName, fill: color, opacity: dimmed ? 0.3 : 1 },
			title: { text: object.name, annotations: searchAnnotations(object.name), opacity: dimmed ? 0.3 : 1 },
			codeText: { text: codeText, annotations: searchAnnotations(codeText), opacity: dimmed ? 0.3 : 1 }
		},
		ports: {
			groups: {
				link: {
					position: { name: "absolute" },
					markup: [
						{ tagName: "circle", selector: "hitArea" },
						{ tagName: "circle", selector: "circle" }
					],
					attrs: {
						hitArea: {
							r: 9,
							magnet: true,
							fill: "transparent",
							stroke: "none",
							pointerEvents: "none"
						},
						circle: {
							r: 2.5,
							stroke: "#4f7df3",
							strokeWidth: 1.2,
							fill: "#ffffff",
							opacity: 0,
							pointerEvents: "none"
						}
					}
				}
			},
			items: [
				{ group: "link", id: "p-left", args: { x: 0, y: NODE_HEIGHT / 2 } },
				{ group: "link", id: "p-right", args: { x: NODE_WIDTH, y: NODE_HEIGHT / 2 } },
				{ group: "link", id: "p-top", args: { x: NODE_WIDTH / 2, y: 0 } },
				{ group: "link", id: "p-bottom", args: { x: NODE_WIDTH / 2, y: NODE_HEIGHT } },
				...connectionPorts(object.id, connections)
			]
		}
	};
}

function connectionPlans() {
	const groups = new Map<string, GraphRelation[]>();
	const visibleIds = new Set(uniqueObjects().map(object => object.id));
	for (const relation of props.relations.filter(item => item.status !== "deprecated")) {
		for (const target of relationTargetIds(relation)) {
			if (!visibleIds.has(relation.sourceType) || !visibleIds.has(target)) {
				continue;
			}
			const key = `${relation.sourceType}\u0000${target}`;
			const list = groups.get(key) ?? [];
			list.push(relation);
			groups.set(key, list);
		}
	}

	const plans: ConnectionPlan[] = [];
	for (const [key, group] of groups) {
		const separator = key.indexOf("\u0000");
		const sourceType = key.slice(0, separator);
		const targetType = key.slice(separator + 1);
		const ports = portPair(sourceType, targetType);
		plans.push({ id: `relation:${encodeURIComponent(sourceType)}:${encodeURIComponent(targetType)}`, sourceType, targetType, group, ports });
	}
	return plans;
}

type ConnectionPlan = {
	id: string;
	sourceType: string;
	targetType: string;
	group: GraphRelation[];
	ports: { source: string; target: string };
};

// Each relationship owns a port. Distribute shared endpoints along the same
// side so arrows, cardinalities and separate relationships remain distinguishable.
function connectionPorts(objectId: string, plans: ConnectionPlan[]) {
	const endpoints: { id: string; side: string; otherId: string }[] = [];
	for (const plan of plans) {
		if (plan.sourceType === objectId) endpoints.push({ id: `${plan.id}:source`, side: plan.ports.source, otherId: plan.targetType });
		if (plan.targetType === objectId) endpoints.push({ id: `${plan.id}:target`, side: plan.ports.target, otherId: plan.sourceType });
	}
	return endpoints.map(endpoint => {
		const verticalSide = endpoint.side === "p-left" || endpoint.side === "p-right";
		const siblings = endpoints.filter(item => item.side === endpoint.side).sort((a, b) => {
			const aPosition = positionOf(a.otherId);
			const bPosition = positionOf(b.otherId);
			return (verticalSide ? aPosition.y - bPosition.y : aPosition.x - bPosition.x) || a.id.localeCompare(b.id);
		});
		const offset = (siblings.indexOf(endpoint) - (siblings.length - 1) / 2) * Math.min(28, 72 / Math.max(1, siblings.length - 1));
		return {
			group: "link", id: endpoint.id,
			args: {
				x: verticalSide ? (endpoint.side === "p-left" ? 0 : NODE_WIDTH) : NODE_WIDTH / 2 + offset,
				y: verticalSide ? NODE_HEIGHT / 2 + offset : (endpoint.side === "p-top" ? 0 : NODE_HEIGHT)
			}
		};
	});
}

function buildEdges(plans: ConnectionPlan[]) {
	const edges: Record<string, unknown>[] = [];
	for (const { id, sourceType, targetType, group, ports } of plans) {
		const first = group[0];
		const selected = group.some(relation => relation.id === props.selectedRelationId);
		const focused = group.some(relation =>
			props.highlightedIds.size > 0
				? props.highlightedIds.has(relation.sourceType) || relationTargetIds(relation).some(target => props.highlightedIds.has(target))
				: relationTouches(selectedIds(), relation)
		);
		const dimmed = hasFocus() && !focused && !selected;
		const color = domainColorOfObject(sourceType);
		const isBundle = group.length > 1;
		const selfLoop = sourceType === targetType;
		const relationName = isBundle ? `${first.name} +${group.length - 1}` : first.name;
		const targetRanges = new Set(group.map(relation => cardinalityText(relation.targetCardinality)));
		const rangeText = [...targetRanges].join(" / ");
		const labelText = first.annotation?.showLabel === false ? "" : `${relationName} · ${rangeText}`;
		const loopPorts = selfLoop ? connectionPorts(sourceType, plans) : [];
		const loopVertices = selfLoop ? selfLoopVertices(
			sourceType,
			loopPorts.find(port => port.id === `${id}:source`)!.args.y,
			loopPorts.find(port => port.id === `${id}:target`)!.args.x
		) : [];
		const glassLabels = [
			...(labelText
				? [
						{
							markup: [
								{ tagName: "title", textContent: labelText },
								{
									tagName: "foreignObject",
									selector: "glass",
									children: [{ tagName: "div", ns: "http://www.w3.org/1999/xhtml", attrs: { style: "width:100%;height:100%;border-radius:13px;backdrop-filter:blur(8px);-webkit-backdrop-filter:blur(8px);pointer-events:none" } }]
								},
								{ tagName: "rect", selector: "body" },
								{ tagName: "text", selector: "label" }
							],
							attrs: {
								glass: {
									ref: "label",
									refWidth: "100%", refWidth2: 20,
									refHeight: "100%", refHeight2: 12,
									refX: -10, refY: -6,
									pointerEvents: "none"
								},
								body: {
									fill: "#ffffff",
									fillOpacity: 0.45,
									stroke: "#ffffff",
									strokeOpacity: 0.85,
									strokeWidth: 1,
									rx: 13,
									ry: 13,
									refWidth: "100%",
									refWidth2: 20,
									refHeight: "100%",
									refHeight2: 12,
									refX: -10,
									refY: -6,
									filter: { name: "relationship-glass" },
									pointerEvents: "all",
									cursor: "pointer"
								},
								label: {
									text: labelText,
									fontSize: 12,
									lineHeight: 16,
									fontWeight: 600,
									fill: selected ? "#183D5D" : "#2B5170",
									textAnchor: "middle",
									textVerticalAnchor: "middle",
									pointerEvents: "none"
								}
							},
							position: 0.5
						}
					]
				: [])
		];
		edges.push({
			id,
			shape: "edge",
			source: { cell: sourceType, port: `${id}:source` },
			target: { cell: targetType, port: `${id}:target` },
			z: 1,
			// Self-relations use a deliberate outside path. The default loop router can
			// fold back through the node when a node is moved close to another one.
			...(selfLoop ? { vertices: loopVertices } : {}),
			...(selfLoop
				? {}
				: {
						router: {
						name: "manhattan",
						args: {
							step: 16,
							padding: { top: 24, right: 24, bottom: 24, left: 24 },
							startDirections: [directionOfPort(ports.source)],
							endDirections: [directionOfPort(ports.target)]
						}
						}
					}),
			connector: { name: "rounded", args: { radius: 10 } },
			attrs: {
				line: {
					stroke: color,
					strokeOpacity: dimmed ? 0.12 : 0.92,
					strokeWidth: selected ? 2.8 : isBundle ? 2.6 : 2,
					strokeLinecap: "round",
					targetMarker: { name: "block", size: 8, fill: color, stroke: color },
					sourceMarker: null
				}
			},
			labels: glassLabels,
			data: { relationId: first.id, relationIds: group.map(relation => relation.id), bundleSize: group.length, selected, focused }
		});
	}
	return edges;
}

function currentDragIds(nodeId: string) {
	const selected = selectedIds();
	return selected.has(nodeId) ? [...selected] : [nodeId];
}

function beginNodeDrag(nodeId: string) {
	if (!graph) return;
	const ids = currentDragIds(nodeId);
	dragState = {
		anchorId: nodeId,
		startPositions: new Map(ids.map(id => {
			const node = graph!.getCellById(id);
			return [id, node?.isNode() ? { ...node.getPosition() } : { x: 0, y: 0 }];
		})),
		moved: false
	};
}

function moveSelectedNodes(nodeId: string, position: Point) {
	if (!graph || !dragState || dragState.anchorId !== nodeId || applyingGroupMove) return;
	const start = dragState.startPositions.get(nodeId) ?? position;
	const snapped = position;
	const dx = snapped.x - start.x;
	const dy = snapped.y - start.y;
	applyingGroupMove = true;
	try {
		for (const [id, origin] of dragState.startPositions) {
			const cell = graph.getCellById(id);
			if (cell?.isNode() && id !== nodeId) cell.setPosition(origin.x + dx, origin.y + dy);
		}
		const anchor = graph.getCellById(nodeId);
		if (anchor?.isNode()) anchor.setPosition(snapped.x, snapped.y);
	}
	finally {
		applyingGroupMove = false;
	}
	dragState.moved = true;
}

function finishNodeDrag() {
	if (!graph || !dragState) return;
	const positions: Record<string, Point> = {};
	for (const id of dragState.startPositions.keys()) {
		const node = graph.getCellById(id);
		if (node?.isNode()) positions[id] = { ...node.getPosition() };
	}
	if (Object.keys(positions).length) emit("nodes-moved", positions);
	dragState = null;
}

function fitAndCenter() {
	if (!graph || !containerRef.value) {
		return false;
	}
	const rect = containerRef.value.getBoundingClientRect();
	if (rect.width < 1 || rect.height < 1 || uniqueObjects().length === 0) {
		return false;
	}
	graph.resize(Math.floor(rect.width), Math.floor(rect.height));
	const nodeBounds = graph.getCellsBBox(graph.getCells());
	if (!nodeBounds) {
		return false;
	}
	graph.zoomToRect(nodeBounds, { padding: GRAPH_PADDING, maxScale: 1 });
	graph.centerPoint(nodeBounds.getCenter().x, nodeBounds.getCenter().y);
	return true;
}

function emitZoom() {
	if (graph) emit("zoom-change", Math.round(graph.zoom() * 100));
}

function zoomIn() {
	graph?.zoom(0.1);
	emitZoom();
}

function zoomOut() {
	graph?.zoom(-0.1);
	emitZoom();
}

function resetZoom() {
	graph?.zoomTo(1);
	emitZoom();
}

function setZoomPercent(percent: number) {
	if (!graph || !Number.isFinite(percent)) return;
	graph.zoomTo(Math.min(3, Math.max(0.25, percent / 100)));
	emitZoom();
}

function centerOn(id: string) {
	const cell = graph?.getCellById(id);
	if (graph && cell?.isNode()) graph.centerCell(cell);
}

function redraw(center = false) {
	if (!graph) {
		return;
	}
	readingModelPositions = true;
	const plans = connectionPlans();
	const cells = [...uniqueObjects().map(object => buildNode(object, plans)), ...buildEdges(plans)] as never[];
	readingModelPositions = false;
	// A model reset removes every previous SVG view in one transaction. This
	// avoids stale views that looked like a copied node after a drag or filter.
	graph.fromJSON({ cells });
	if ((center || !centeredOnce) && uniqueObjects().length > 0) {
		centeredOnce = fitAndCenter();
	}
}

function updateConnectedEdges() {
	if (!graph) return;
	const plans = connectionPlans();
	for (const node of graph.getNodes()) {
		for (const port of connectionPorts(node.id, plans)) {
			node.portProp(port.id, "args", port.args);
		}
	}
	for (const metadata of buildEdges(plans)) {
		const edge = graph.getCellById(metadata.id as string);
		if (!edge?.isEdge()) continue;
		edge.setSource(metadata.source as { cell: string; port: string });
		edge.setTarget(metadata.target as { cell: string; port: string });
		if (metadata.router) edge.setRouter(metadata.router as { name: string; args: Record<string, unknown> });
		edge.setVertices((metadata.vertices ?? []) as Point[]);
		edge.setLabels(metadata.labels as never[]);
	}
}

function updateSelection() {
	if (!graph) return;
	for (const node of graph.getNodes()) {
		const object = uniqueObjects().find(item => item.id === node.id);
		if (!object) continue;
		const color = domainColor(object.domainId);
		const focused = nodeIsFocused(node.id);
		const selected = selectedIds().has(node.id) || props.selectedObjectId === node.id;
		const dimmed = hasFocus() && !focused;
		const nodeOpacity = dimmed ? 0.24 : object.status === "deprecated" ? 0.45 : 1;
		node.attr({
			body: { stroke: selected || props.highlightedIds.has(node.id) ? color : "#D9E7F5", strokeWidth: selected || props.highlightedIds.has(node.id) ? 1.5 : 1, opacity: nodeOpacity },
			headerBg: { opacity: nodeOpacity },
			divider: { opacity: nodeOpacity },
			iconChip: { opacity: nodeOpacity },
			objectIcon: { opacity: nodeOpacity },
			identityIcon: { opacity: nodeOpacity },
			domainLabel: { opacity: dimmed ? 0.3 : 1 },
			statusDot: { opacity: dimmed ? 0.25 : 1 },
			title: { annotations: searchAnnotations(object.name), opacity: dimmed ? 0.3 : 1 },
			codeText: { annotations: searchAnnotations(object.id.toUpperCase()), opacity: dimmed ? 0.3 : 1 },
			idChipBg: { opacity: dimmed ? 0.2 : 1 },
			idChipText: { annotations: searchAnnotations(object.identityAttrNames.length ? object.identityAttrNames.join(" · ") : object.id), opacity: dimmed ? 0.3 : 1 },
			attrsChipText: { opacity: dimmed ? 0.3 : 1 },
			attrsChipBg: { opacity: object.attributeCount == null ? 0 : dimmed ? 0.2 : 1 },
			relsChipBg: { opacity: dimmed ? 0.2 : 1 },
			relsChipText: { opacity: dimmed ? 0.3 : 1 }
		});
	}
	for (const edge of graph.getEdges()) {
		const data = edge.getData() as { relationId?: string; relationIds?: string[]; bundleSize?: number; selected?: boolean; focused?: boolean } | undefined;
		if (!data?.relationIds) continue;
		const group = props.relations.filter(relation => data.relationIds!.includes(relation.id));
		const selected = !!props.selectedRelationId && data.relationIds.includes(props.selectedRelationId);
		const focused = group.some(relation =>
			props.highlightedIds.size > 0
				? props.highlightedIds.has(relation.sourceType) || relationTargetIds(relation).some(target => props.highlightedIds.has(target))
				: relationTouches(selectedIds(), relation)
		);
		const dimmed = hasFocus() && !focused && !selected;
		edge.setData({ ...data, selected, focused });
		edge.attr("line/strokeOpacity", dimmed ? 0.12 : 0.92);
		edge.attr("line/strokeWidth", selected ? 2.8 : data.bundleSize && data.bundleSize > 1 ? 2.6 : 2);
		edge.getLabels().forEach((label, index) => {
			const fill = label.attrs?.label?.fontSize === 12
				? selected ? "#183D5D" : dimmed ? "#9AAABC" : "#2B5170"
				: selected ? "#647E98" : dimmed ? "#B7C3D0" : "#8FA3B8";
			edge.prop(["labels", String(index), "attrs", "label", "fill"], fill);
		});
	}
}

onMounted(() => {
	Graph.registerFilter("relationship-glass", () => `<filter>
		<feGaussianBlur in="SourceAlpha" stdDeviation="3" result="blur" />
		<feOffset in="blur" dy="2" result="shadow" />
		<feFlood flood-color="#254561" flood-opacity="0.16" />
		<feComposite in2="shadow" operator="in" />
		<feMerge><feMergeNode /><feMergeNode in="SourceGraphic" /></feMerge>
	</filter>`, true);

	Graph.registerNode(
		"object-node",
		{
			width: NODE_WIDTH,
			height: NODE_HEIGHT,
			markup: [
				{ tagName: "rect", selector: "body" },
				// 头部染色区 + 分隔线（迭代五结构：领域色淡染而非色带）
				{ tagName: "path", selector: "headerBg" },
				{ tagName: "rect", selector: "divider" },
				{ tagName: "rect", selector: "iconChip" },
				{ tagName: "path", selector: "objectIcon" },
				{ tagName: "text", selector: "domainLabel" },
				{ tagName: "circle", selector: "statusDot" },
				{ tagName: "text", selector: "title" },
				{ tagName: "text", selector: "codeText" },
				{ tagName: "rect", selector: "idChipBg" },
				{ tagName: "path", selector: "identityIcon" },
				{ tagName: "text", selector: "idChipText" },
				{ tagName: "rect", selector: "attrsChipBg" },
				{ tagName: "text", selector: "attrsChipText" },
				{ tagName: "rect", selector: "relsChipBg" },
				{ tagName: "text", selector: "relsChipText" }
			],
			attrs: {
				body: {
					refWidth: "100%",
					refHeight: "100%",
					rx: 12,
					ry: 12,
					fill: "#ffffff",
					stroke: "#D9E7F5",
					strokeWidth: 1,
					cursor: "pointer"
				},
				headerBg: { d: `M12 0 H${NODE_WIDTH - 12} Q${NODE_WIDTH} 0 ${NODE_WIDTH} 12 V44 H0 V12 Q0 0 12 0 Z` },
				divider: { x: 0, y: 43, width: NODE_WIDTH, height: 1 },
				objectIcon: { d: "M24 14H32L36 18V29H24Z M32 14V18H36 M27 22H33 M27 25H31", fill: "none", strokeWidth: 1.5, strokeLinecap: "round", strokeLinejoin: "round" },
				identityIcon: { d: "M23 102a2.5 2.5 0 1 0 0 .1 M25 104l4 4 M27 106l1-1", fill: "none", strokeWidth: 1.4, strokeLinecap: "round" },
				iconChip: { x: 14, y: 8, width: 28, height: 28, rx: 8, ry: 8 },
				domainLabel: {
					refX: 50,
					refY: 22,
					fontSize: 10,
					fontWeight: 700,
					textAnchor: "start",
					textVerticalAnchor: "middle",
					letterSpacing: 0.5
				},
				statusDot: { cx: NODE_WIDTH - 20, cy: 22, r: 4.5, stroke: "#ffffff", strokeWidth: 2 },
				title: {
					refX: 14,
					refY: 64,
					fontSize: 16,
					fontWeight: 700,
					fill: "#16324F",
					textAnchor: "start",
					textVerticalAnchor: "middle",
					textWrap: { width: NODE_WIDTH - 28, height: 24, ellipsis: true }
				},
				codeText: {
					refX: 14,
					refY: 82,
					fontSize: 10,
					fill: "#93A9BE",
					textAnchor: "start",
					textVerticalAnchor: "middle",
					letterSpacing: 1,
					textWrap: { width: NODE_WIDTH - 28, height: 13, ellipsis: true }
				},
				idChipBg: { x: 14, y: 94, width: 96, height: 20, rx: 10, ry: 10 },
				idChipText: {
					refX: 35,
					refY: 104,
					fontSize: 10,
					fontWeight: 500,
					textAnchor: "start",
					textVerticalAnchor: "middle",
					textWrap: { width: 68, height: 20, ellipsis: true }
				},
				attrsChipBg: { x: 116, y: 94, width: 48, height: 20, rx: 10, ry: 10 },
				attrsChipText: {
					refX: 140,
					refY: 104,
					fontSize: 10,
					fontWeight: 500,
					fill: "#6A87A3",
					textAnchor: "middle",
					textVerticalAnchor: "middle"
				},
				relsChipBg: { x: 170, y: 94, width: 48, height: 20, rx: 10, ry: 10 },
				relsChipText: {
					refX: 194,
					refY: 104,
					fontSize: 10,
					fontWeight: 500,
					fill: "#6A87A3",
					textAnchor: "middle",
					textVerticalAnchor: "middle"
				}
			}
		},
		true
	);

	graph = new Graph({
		container: containerRef.value!,
		grid: {
			visible: true,
			type: "dot",
			size: 24,
			args: { color: "rgba(26, 80, 130, 0.1)", thickness: 1 }
		},
		background: { color: "transparent" },
		panning: true,
		embedding: false,
		mousewheel: { enabled: true, modifiers: ["ctrl"], zoomAtMousePosition: true },
		interacting: {
			nodeMovable: true,
			edgeMovable: false,
			edgeLabelMovable: false,
			arrowheadMovable: false,
			vertexMovable: false,
			vertexAddable: false,
			vertexDeletable: false,
			useEdgeTools: false,
			magnetConnectable: true,
			stopDelegateOnDragging: true
		},
		connecting: {
			snap: { radius: 28 },
			// Edges end at the port center, independent of its invisible hit area.
			connectionPoint: "anchor",
			allowBlank: false,
			allowLoop: true,
			allowNode: false,
			allowPort: true,
			allowEdge: false,
			allowMulti: false,
			highlight: true,
			router: { name: "manhattan", args: { step: 16, padding: { top: 24, right: 24, bottom: 24, left: 24 } } },
			connector: { name: "rounded", args: { radius: 10 } },
		createEdge() {
			return graph!.createEdge({
				shape: "edge",
				attrs: {
					line: {
						stroke: "#38A6F2",
						strokeWidth: 2,
						strokeDasharray: "6 4",
						strokeLinecap: "round",
						targetMarker: { name: "block", size: 8, fill: "#38A6F2", stroke: "#38A6F2" }
					}
				}
			});
		}
		}
	});

	graph.on("node:click", ({ node, e }) => {
		if (dragState?.moved) return;
		const additive = e.shiftKey || e.ctrlKey || e.metaKey;
		const current = new Set(props.selectedObjectIds ?? []);
		if (additive) {
			if (current.has(node.id)) current.delete(node.id);
			else current.add(node.id);
			emit("selection-change", [...current]);
			return;
		}
		emit("selection-change", [node.id]);
		emit("select-object", node.id);
	});
	graph.on("node:mouseenter", ({ node }) => {
		node.attr("body/stroke", domainColorOfObject(node.id));
		for (const portId of ["p-left", "p-right", "p-top", "p-bottom"]) {
			node.portProp(portId, "attrs/circle/opacity", 0.9);
			node.portProp(portId, "attrs/hitArea/pointerEvents", "all");
		}
	});
	graph.on("node:mouseleave", ({ node }) => {
		node.attr("body/stroke", props.highlightedIds.has(node.id) || selectedIds().has(node.id) || props.selectedObjectId === node.id ? domainColorOfObject(node.id) : "#D9E7F5");
		for (const portId of ["p-left", "p-right", "p-top", "p-bottom"]) {
			node.portProp(portId, "attrs/circle/opacity", 0);
			node.portProp(portId, "attrs/hitArea/pointerEvents", "none");
		}
	});
	graph.on("edge:click", ({ edge }) => {
		emit("selection-change", []);
		const relationId = (edge.getData() as { relationId?: string } | undefined)?.relationId;
		if (relationId) {
			emit("select-relation", relationId);
		}
	});
	graph.on("edge:mouseenter", ({ edge }) => {
		const data = edge.getData() as { bundleSize?: number; selected?: boolean };
		edge.attr("line/strokeWidth", data.selected ? 3.3 : data.bundleSize && data.bundleSize > 1 ? 3.1 : 2.5);
		edge.getLabels().forEach((label, index) => {
			if (label.attrs?.label?.fontSize === 12) edge.prop(["labels", String(index), "attrs", "label", "fill"], "#183D5D");
		});
	});
	graph.on("edge:mouseleave", ({ edge }) => {
		const data = edge.getData() as { bundleSize?: number; selected?: boolean };
		edge.attr("line/strokeWidth", data.selected ? 2.8 : data.bundleSize && data.bundleSize > 1 ? 2.6 : 2);
		edge.getLabels().forEach((label, index) => {
			if (label.attrs?.label?.fontSize === 12) edge.prop(["labels", String(index), "attrs", "label", "fill"], data.selected ? "#183D5D" : "#2B5170");
		});
	});
	graph.on("blank:click", () => {
		emit("selection-change", []);
		emit("canvas-click");
	});
	graph.on("node:mousedown", ({ node, e }) => {
		if (e.button === 0) beginNodeDrag(node.id);
	});
	graph.on("node:moving", ({ node }) => {
		movingNodeId = node.id;
		moveSelectedNodes(node.id, node.getPosition());
		updateConnectedEdges();
	});
	graph.on("node:moved", () => {
		movingNodeId = null;
		finishNodeDrag();
		if (redrawPending) {
			redrawPending = false;
			redraw();
		}
	});
	graph.on("scale", emitZoom);
	graph.on("edge:connected", ({ isNew, edge }) => {
		if (!isNew) {
			return;
		}
		const sourceId = edge.getSourceCell()?.id;
		const targetId = edge.getTargetCell()?.id;
		graph!.removeEdge(edge.id);
		if (sourceId && targetId) {
			emit("create-relation", sourceId, targetId);
		}
	});

	redraw(true);
	resizeObserver = new ResizeObserver(() => {
		if (!graph || !containerRef.value) {
			return;
		}
		const rect = containerRef.value.getBoundingClientRect();
		if (rect.width < 1 || rect.height < 1) {
			return;
		}
		graph.resize(Math.floor(rect.width), Math.floor(rect.height));
		if (!centeredOnce && uniqueObjects().length > 0) {
			centeredOnce = fitAndCenter();
		}
	});
	if (containerRef.value) {
		resizeObserver.observe(containerRef.value);
	}
});

watch(
	() => [props.objects, props.relations, props.domainColorMap, props.domainNameMap],
	() => {
		if (movingNodeId) {
			redrawPending = true;
			return;
		}
		redraw();
	},
	{ deep: true }
);

// Selection and drawer changes update existing SVG attributes; they do not rebuild
// the graph, which keeps blank-canvas clicks free of visible flicker.
watch(() => [props.searchText, props.highlightedIds, props.selectedObjectIds, props.selectedObjectId, props.selectedRelationId], updateSelection, { deep: true });

onBeforeUnmount(() => {
	resizeObserver?.disconnect();
	resizeObserver = null;
	graph?.dispose();
	graph = null;
});

function zoomToFit() {
	fitAndCenter();
}

defineExpose({ zoomToFit, zoomIn, zoomOut, resetZoom, setZoomPercent, centerOn });
</script>

<style scoped>
.object-canvas {
	position: relative;
	width: 100%;
	height: 100%;
	min-height: 0;
	/* 迭代五：霜白微蓝底（近乎留白）+ 天蓝/薄荷青极轻氛围光斑，层次由节点投影承担 */
	background:
		radial-gradient(circle at 15% 0%, rgba(56, 166, 242, 0.07), transparent 26%),
		radial-gradient(circle at 85% 100%, rgba(20, 200, 180, 0.06), transparent 28%),
		#f7fafd;
}

:deep(.x6-node) {
	/* 三层分层投影：贴边定义 + 结构深度 + 领域色氛围光 */
	filter:
		drop-shadow(0 1px 2px rgba(33, 51, 79, 0.04))
		drop-shadow(0 6px 16px rgba(33, 51, 79, 0.06))
		drop-shadow(0 12px 28px rgba(56, 134, 201, 0.12));
	transition: filter 0.2s ease;
}

:deep(.x6-node:hover) {
	filter:
		drop-shadow(0 1px 2px rgba(33, 51, 79, 0.05))
		drop-shadow(0 6px 16px rgba(33, 51, 79, 0.08))
		drop-shadow(0 12px 28px rgba(56, 134, 201, 0.18));
}

</style>
