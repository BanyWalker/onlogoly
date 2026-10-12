package com.xiyuan.ontology.modeling.storage;

import java.util.function.Function;

public interface ModelStorage {

	<T> T read(String workspaceId, Function<ModelReadSession, T> action);

	<T> T write(String workspaceId, Function<ModelWriteSession, T> action);

}
