package com.amuguide.backend.chat.service;

import com.amuguide.backend.entity.Prestation;
import com.amuguide.backend.entity.StructureSante;

import java.util.List;

public record AgentContext(
        List<Prestation> prestations,
        List<StructureSante> structures,
        List<MedicationEvidence> medications,
        boolean amuInfoRelevant
) {
    public boolean hasEvidence() {
        return !prestations.isEmpty() || !structures.isEmpty() || !medications.isEmpty() || amuInfoRelevant;
    }
}
