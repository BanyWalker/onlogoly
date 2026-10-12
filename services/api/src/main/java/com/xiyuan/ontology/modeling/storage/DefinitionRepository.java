package com.xiyuan.ontology.modeling.storage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.xiyuan.ontology.modeling.model.ModelDefinition;

public interface DefinitionRepository {

	Optional<ModelDefinition> findById(String id);

	List<ModelDefinition> findAll();

	List<ModelDefinition> findByDomain(String domainId);

	interface Writable extends DefinitionRepository {
		void save(ModelDefinition definition);

		void delete(UUID elementKey);
	}

}
