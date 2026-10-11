import type { messages } from "@/locales/messages";

type WidenStrings<T> = { [K in keyof T]: T[K] extends string ? string : WidenStrings<T[K]> };
export type Schema = WidenStrings<(typeof messages)["en"]>;

declare module "vue-i18n" {
	export interface DefineLocaleMessage extends Schema {
		__schemaBrand?: never;
	}
}
