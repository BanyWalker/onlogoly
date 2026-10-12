package com.xiyuan.ontology.modeling.codec;

import java.util.LinkedHashMap;
import java.util.Map;

import com.xiyuan.ontology.modeling.model.WorkspaceSnapshot;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

public final class SnapshotJsonCodec {

	private final JsonMapper mapper = JsonMapper.builder().findAndAddModules()
		.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

	public byte[] encode(WorkspaceSnapshot snapshot) {
		Map<String, Object> envelope = new LinkedHashMap<>();
		envelope.put("kind", "xiyuan-storage-snapshot");
		envelope.put("snapshot", snapshot);
		return this.mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(envelope);
	}

	public WorkspaceSnapshot decode(byte[] bytes) {
		var envelope = this.mapper.readTree(bytes);
		if (!"xiyuan-storage-snapshot".equals(envelope.path("kind").asString())) {
			throw new IllegalArgumentException("Invalid migration snapshot kind");
		}
		return this.mapper.treeToValue(envelope.path("snapshot"), WorkspaceSnapshot.class);
	}

}
