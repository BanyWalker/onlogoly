// Type colours follow the instance prototype, independently of domain colours.
const TYPE_COLORS: Record<string, string> = {
	ALERT: "#FF7B72",
	SERVICE: "#3D8BF7",
	APPLICATION: "#A97BF0",
	HOST: "#F5A524",
	INFRASTRUCTUREASSET: "#F5A524",
	SERVICEINSTANCE: "#17C3A2",
	INCIDENT: "#17C3A2",
	WORKORDER: "#33C3D6"
};
const PALETTE = ["#3D8BF7", "#A97BF0", "#F5A524", "#FF7B72", "#17C3A2", "#33C3D6", "#DB86BE", "#7E91C8"];

export const FACT_EDGE_COLOR = "#C9D4E4";

export function factTypeColor(typeId: string) {
	const name = typeId
		.toUpperCase()
		.replace(/^OBJ[-_]?/, "")
		.replace(/[-_\s]/g, "");
	if (TYPE_COLORS[name]) return TYPE_COLORS[name];
	// Hash the stable ID rather than a list index: filtering and model reordering
	// must never change the colour of an existing type.
	let hash = 0;
	for (const char of typeId) hash = (hash * 31 + char.charCodeAt(0)) >>> 0;
	return PALETTE[hash % PALETTE.length]!;
}
