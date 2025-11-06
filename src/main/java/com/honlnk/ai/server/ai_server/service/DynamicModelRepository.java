package com.honlnk.ai.server.ai_server.service;

import com.honlnk.ai.server.ai_server.model.DynamicModelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DynamicModelRepository extends JpaRepository<DynamicModelEntity, Long> {

    DynamicModelEntity findByModelName(String modelName);

    DynamicModelEntity findByIsDefaultTrue();
}