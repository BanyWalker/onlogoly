import { relationTargetIds, type GraphObject, type GraphRelation } from "./types";

export type Point = { x: number; y: number };
export type Positions = Record<string, Point>;
export type LayoutCommand = { before: Positions; after: Positions };
export type AlignAction = "left" | "center" | "right" | "top" | "middle" | "bottom" | "distribute-x" | "distribute-y";

export const NODE_WIDTH = 232;
export const NODE_HEIGHT = 124;

// Collapse cycles before assigning layers, so reciprocal and self relations
// cannot leave nodes unplaced or make the layout loop forever.
export function layeredLayout(objects: GraphObject[], relations: GraphRelation[], direction: "horizontal" | "vertical"): Positions {
	if (!objects.length) return {};
	const ids = objects.map(object => object.id);
	const visible = new Set(ids);
	const adjacency = new Map(ids.map(id => [id, new Set<string>()]));
	for (const relation of relations) {
		if (relation.status === "deprecated" || !visible.has(relation.sourceType)) continue;
		for (const target of relationTargetIds(relation)) {
			if (visible.has(target) && target !== relation.sourceType) adjacency.get(relation.sourceType)!.add(target);
		}
	}
	let index = 0;
	const indices = new Map<string, number>();
	const low = new Map<string, number>();
	const stack: string[] = [];
	const onStack = new Set<string>();
	const components: string[][] = [];
	function visit(id: string) {
		indices.set(id, index);
		low.set(id, index++);
		stack.push(id);
		onStack.add(id);
		for (const target of adjacency.get(id)!) {
			if (!indices.has(target)) {
				visit(target);
				low.set(id, Math.min(low.get(id)!, low.get(target)!));
			}
			else if (onStack.has(target)) low.set(id, Math.min(low.get(id)!, indices.get(target)!));
		}
		if (low.get(id) === indices.get(id)) {
			const component: string[] = [];
			let member: string;
			do {
				member = stack.pop()!;
				onStack.delete(member);
				component.push(member);
			} while (member !== id);
			components.push(component);
		}
	}
	ids.forEach(id => { if (!indices.has(id)) visit(id); });
	const componentOf = new Map(components.flatMap((members, component) => members.map(id => [id, component] as const)));
	const successors = components.map(() => new Set<number>());
	const inDegree = components.map(() => 0);
	for (const [source, targets] of adjacency) {
		for (const target of targets) {
			const from = componentOf.get(source)!;
			const to = componentOf.get(target)!;
			if (from !== to && !successors[from].has(to)) {
				successors[from].add(to);
				inDegree[to]++;
			}
		}
	}
	const queue = inDegree.map((degree, component) => degree === 0 ? component : -1).filter(component => component >= 0);
	const ranks = components.map(() => 0);
	for (let cursor = 0; cursor < queue.length; cursor++) {
		const from = queue[cursor];
		for (const to of successors[from]) {
			ranks[to] = Math.max(ranks[to], ranks[from] + 1);
			if (--inDegree[to] === 0) queue.push(to);
		}
	}
	const layers = new Map<number, GraphObject[]>();
	for (const object of objects) {
		const rank = ranks[componentOf.get(object.id)!];
		const layer = layers.get(rank) ?? [];
		layer.push(object);
		layers.set(rank, layer);
	}
	const horizontal = direction === "horizontal";
	const originX = Math.min(...objects.map(object => object.layout.x));
	const originY = Math.min(...objects.map(object => object.layout.y));
	const maxBreadth = Math.max(...[...layers.values()].map(layer => layer.length));
	const breadthStep = horizontal ? NODE_HEIGHT + 80 : NODE_WIDTH + 80;
	const depthStep = horizontal ? NODE_WIDTH + 160 : NODE_HEIGHT + 140;
	const positions: Positions = {};
	for (const [rank, layer] of layers) {
		layer.sort((a, b) => (horizontal ? a.layout.y - b.layout.y : a.layout.x - b.layout.x) || a.id.localeCompare(b.id));
		layer.forEach((object, order) => {
			const breadth = (order + (maxBreadth - layer.length) / 2) * breadthStep;
			positions[object.id] = { x: Math.round(originX + (horizontal ? rank * depthStep : breadth)), y: Math.round(originY + (horizontal ? breadth : rank * depthStep)) };
		});
	}
	return positions;
}

export function alignedPositions(positions: Positions, action: AlignAction): Positions {
	const entries = Object.entries(positions);
	if (entries.length < 2) return {};
	const axis = ["left", "center", "right", "distribute-x"].includes(action) ? "x" : "y";
	const values = entries.map(([, point]) => point[axis]);
	const min = Math.min(...values);
	const max = Math.max(...values);
	const result: Positions = {};
	if (action.startsWith("distribute")) {
		if (entries.length < 3) return {};
		entries.sort((a, b) => a[1][axis] - b[1][axis]);
		entries.forEach(([id, point], order) => { result[id] = { ...point, [axis]: Math.round(min + (max - min) * order / (entries.length - 1)) }; });
	}
	else {
		const value = ["left", "top"].includes(action) ? min : ["right", "bottom"].includes(action) ? max : (min + max) / 2;
		for (const [id, point] of entries) result[id] = { ...point, [axis]: Math.round(value) };
	}
	return result;
}

export function changedPositions(before: Positions, after: Positions): LayoutCommand {
	const command: LayoutCommand = { before: {}, after: {} };
	for (const [id, point] of Object.entries(after)) {
		if (before[id] && (before[id].x !== point.x || before[id].y !== point.y)) {
			command.before[id] = { ...before[id] };
			command.after[id] = { x: Math.round(point.x), y: Math.round(point.y) };
		}
	}
	return command;
}

export function snapPosition(point: Point, stationary: Point[], tolerance: number, grid = false) {
	const result = grid ? { x: Math.round(point.x / 24) * 24, y: Math.round(point.y / 24) * 24 } : { ...point };
	const guides: { x?: number; y?: number } = {};
	for (const axis of ["x", "y"] as const) {
		const size = axis === "x" ? NODE_WIDTH : NODE_HEIGHT;
		let best = tolerance + 1;
		for (const other of stationary) {
			for (const offset of [0, size / 2, size]) {
				const distance = other[axis] - point[axis];
				if (Math.abs(distance) <= tolerance && Math.abs(distance) < best) {
					best = Math.abs(distance);
					result[axis] = other[axis];
					guides[axis] = other[axis] + offset;
				}
			}
		}
	}
	return { point: result, guides };
}
