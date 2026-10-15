import {
	Bell,
	Collection,
	Connection,
	Cpu,
	DataAnalysis,
	Document,
	DocumentCopy,
	EditPen,
	Files,
	Grid,
	Link,
	Odometer,
	Operation,
	SetUp
} from "@element-plus/icons-vue";
import { markRaw, type Component } from "vue";

export const appIconRegistry = {
	dashboard: markRaw(Odometer),
	list: markRaw(Files),
	detail: markRaw(DocumentCopy),
	policy: markRaw(Grid),
	form: markRaw(EditPen),
	audit: markRaw(Document),
	ticket: markRaw(Bell),
	objectModel: markRaw(Connection),
	serviceAccess: markRaw(Link),
	apiService: markRaw(Link),
	mcpService: markRaw(Cpu),
	functionService: markRaw(Operation),
	factAccess: markRaw(DataAnalysis),
	objectInstance: markRaw(Connection),
	terminology: markRaw(Collection),
	ruleModel: markRaw(SetUp),
	inspectionReport: markRaw(DataAnalysis)
} satisfies Record<string, Component>;

export type AppIconName = keyof typeof appIconRegistry;

export function resolveAppIcon(icon?: AppIconName) {
	return icon ? appIconRegistry[icon] : undefined;
}
