<template>
	<ElDialog v-model="visible" title="自定义新领域" width="420px" append-to-body>
		<ElForm label-width="90px">
			<ElFormItem label="名称" required>
				<ElInput v-model="form.name" placeholder="如：网络管理" />
			</ElFormItem>
			<ElFormItem label="业务说明">
				<ElInput v-model="form.description" type="textarea" :rows="2" />
			</ElFormItem>
			<ElFormItem label="责任角色" required>
				<ElInput v-model="form.ownerRole" placeholder="如：网络建模负责人" />
			</ElFormItem>
		</ElForm>
		<template #footer>
			<ElButton @click="visible = false">取消</ElButton>
			<ElButton type="primary" :loading="submitting" @click="submit">创建并选入</ElButton>
		</template>
	</ElDialog>
</template>

<script setup lang="ts">
import { ElMessage } from "element-plus";
import { reactive, ref } from "vue";

import { objectModelApi } from "../api";
import type { DomainDef } from "../types";
import type { ApiError } from "../types";

const emit = defineEmits<{
	(e: "created", domain: DomainDef): void;
}>();

const visible = ref(false);
const submitting = ref(false);
const form = reactive({ name: "", description: "", ownerRole: "" });

function open() {
	form.name = "";
	form.description = "";
	form.ownerRole = "";
	visible.value = true;
}

async function submit() {
	if (!form.name.trim() || !form.ownerRole.trim()) {
		ElMessage.warning("名称与责任角色为必填项");
		return;
	}
	submitting.value = true;
	try {
		const domain = await objectModelApi.createDomain({
			name: form.name.trim(),
			description: form.description.trim() || undefined,
			ownerRole: form.ownerRole.trim()
		});
		ElMessage.success(`领域 ${domain.name} 已创建`);
		emit("created", domain);
		visible.value = false;
	}
	catch (error) { ElMessage.error((error as ApiError).message || "领域创建失败"); }
	finally {
		submitting.value = false;
	}
}

defineExpose({ open });
</script>
