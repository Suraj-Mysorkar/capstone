package com.bank.digital.lending.service;

import com.bank.digital.lending.model.dto.LoanSchemeDTO;
import com.bank.digital.lending.model.entity.LoanScheme;
import com.bank.digital.lending.repository.LoanSchemeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
public class LoanSchemeService {

    private final LoanSchemeRepository schemeRepository;

    public LoanSchemeService(LoanSchemeRepository schemeRepository) {
        this.schemeRepository = schemeRepository;
    }

    @Transactional(readOnly = true)
    public List<LoanSchemeDTO> getActiveSchemes() {
        return schemeRepository.findByIsActiveTrue()
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<LoanScheme> getSchemeEntityById(String schemeId) {
        return schemeRepository.findById(schemeId);
    }

    @Transactional(readOnly = true)
    public Optional<LoanSchemeDTO> getSchemeById(String schemeId) {
        return schemeRepository.findById(schemeId).map(this::mapToDTO);
    }

    private LoanSchemeDTO mapToDTO(LoanScheme entity) {
        return new LoanSchemeDTO(
                entity.getSchemeId(),
                entity.getLoanType(),
                entity.getSchemeName(),
                entity.getMinAmount(),
                entity.getMaxAmount(),
                entity.getMinTenureMonths(),
                entity.getMaxTenureMonths(),
                entity.getBaseInterestRate(),
                entity.getIsActive()
        );
    }
}
