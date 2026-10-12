package com.xiyuan.ontology.modeling.model;

import java.util.UUID;

import com.xiyuan.ontology.modeling.model.ModelDefinition.Kind;

public record ElementIdentity(UUID elementKey, Kind kind, String id, boolean deleted) {

}
