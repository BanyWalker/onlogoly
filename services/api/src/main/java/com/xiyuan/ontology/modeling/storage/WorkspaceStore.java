package com.xiyuan.ontology.modeling.storage;

import java.util.function.Function;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 工作空间存储抽象（TDD-OM-002 §10.5 契约不变原则）：API 与服务层只依赖本接口，
 * 文件实现与未来数据库实现可替换，API 契约与 JSON 结构不随存储迁移改变。
 */
public interface WorkspaceStore {

	/**
	 * 读操作：在工作空间锁内对主文档快照执行只读函数。
	 */
	<T> T read(Function<ObjectNode, T> reader);

	/**
	 * 写操作：在工作空间锁内对主文档副本执行修改函数，成功后原子落盘；
	 * 修改函数抛出异常时内存态回滚、不落盘（§10.2 保存流程）。
	 */
	<T> T mutate(Function<ObjectNode, T> mutator);

	/**
	 * 追加元素修订记录（revisions/&lt;elementId&gt;.jsonl，append-only）。
	 */
	void appendRevision(String elementId, ObjectNode revisionRecord);

	/**
	 * 追加变更事件（events.jsonl，append-only 审计日志）。
	 */
	void appendEvent(ObjectNode event);

	/**
	 * 当前布局修订号。
	 */
	int layoutRevision();

	/**
	 * 保存布局（独立 layoutRevision，不产生定义修订，§2.1/§9.1）。
	 */
	int saveLayout(int expectedRevision, ObjectNode positions);

	ObjectNode newEvent(String eventType, String elementId);

}
