<template>
	<div class="api-auth-editor">
		<div class="api-auth-line">
			<ElFormItem label="认证方式">
				<ElSelect :model-value="modelValue.authType ?? 'NONE'" @update:model-value="update({ authType: $event })">
					<ElOption label="继承服务认证" value="INHERIT" />
					<ElOption label="无需认证" value="NONE" />
					<ElOption label="API Key" value="API_KEY" />
					<ElOption label="Bearer Token" value="BEARER" />
					<ElOption label="Basic Auth" value="BASIC" />
					<ElOption label="自定义 Header" value="CUSTOM_HEADER" />
				</ElSelect>
			</ElFormItem>
			<ElFormItem v-if="modelValue.authType === 'API_KEY'" label="Key 名称">
				<ElInput :model-value="modelValue.apiKeyName" placeholder="X-API-Key" @update:model-value="update({ apiKeyName: $event })" />
			</ElFormItem>
			<ElFormItem v-if="modelValue.authType === 'API_KEY'" label="传递位置">
				<ElSelect :model-value="modelValue.apiKeyIn ?? 'header'" @update:model-value="update({ apiKeyIn: $event })"><ElOption label="Header" value="header" /><ElOption label="Query" value="query" /></ElSelect>
			</ElFormItem>
		</div>
		<ElFormItem v-if="modelValue.authType === 'API_KEY'" label="API Key 值"><ElInput :model-value="modelValue.apiKeyValue" show-password placeholder="支持 {{变量名}}" @update:model-value="update({ apiKeyValue: $event })" /></ElFormItem>
		<ElFormItem v-if="modelValue.authType === 'BEARER'" label="Token"><ElInput :model-value="modelValue.token" show-password placeholder="支持 {{变量名}}" @update:model-value="update({ token: $event })" /></ElFormItem>
		<div v-if="modelValue.authType === 'BASIC'" class="api-auth-line"><ElFormItem label="用户名"><ElInput :model-value="modelValue.username" @update:model-value="update({ username: $event })" /></ElFormItem><ElFormItem label="密码"><ElInput :model-value="modelValue.password" show-password @update:model-value="update({ password: $event })" /></ElFormItem></div>
		<div v-if="modelValue.authType === 'CUSTOM_HEADER'" class="api-auth-line"><ElFormItem label="Header 名称"><ElInput :model-value="modelValue.headerName" placeholder="X-Auth-Token" @update:model-value="update({ headerName: $event })" /></ElFormItem><ElFormItem label="Header 值"><ElInput :model-value="modelValue.headerValue" show-password placeholder="支持 {{变量名}}" @update:model-value="update({ headerValue: $event })" /></ElFormItem></div>
		<p v-pre class="api-editor-hint">敏感值仅用于调试请求；可填写 {{变量名}}，发送时由环境变量替换。</p>
	</div>
</template>

<script setup lang="ts">
import type { ApiAuthConfig } from "../types";

const props = defineProps<{ modelValue: ApiAuthConfig }>();
const emit = defineEmits<{ "update:modelValue": [value: ApiAuthConfig] }>();
function update(patch: Partial<ApiAuthConfig>) {
	const next = { ...props.modelValue, ...patch };
	if (patch.authType === "INHERIT") next.inherit = true;
	else if (patch.authType) next.inherit = false;
	emit("update:modelValue", next);
}
</script>
