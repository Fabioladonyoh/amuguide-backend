package com.amuguide.backend.repository;

import com.amuguide.backend.entity.StructureSante;
import com.amuguide.backend.enums.TypeStructure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StructureSanteRepository extends JpaRepository<StructureSante, Long> {

        List<StructureSante> findByVilleIgnoreCase(String ville);

        List<StructureSante> findByType(TypeStructure type);

        List<StructureSante> findByAgrementAMUTrue();


}
