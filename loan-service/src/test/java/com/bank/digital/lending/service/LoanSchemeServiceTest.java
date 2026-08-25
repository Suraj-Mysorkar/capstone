package com.bank.digital.lending.service;

import com.bank.digital.lending.model.dto.LoanSchemeDTO;
import com.bank.digital.lending.model.entity.LoanScheme;
import com.bank.digital.lending.model.enums.LoanType;
import com.bank.digital.lending.repository.LoanSchemeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanSchemeServiceTest {

    @Mock
    private LoanSchemeRepository schemeRepository;

    @InjectMocks
    private LoanSchemeService schemeService;

    private LoanScheme personalLoanScheme;
    private LoanScheme homeLoanScheme;

    @BeforeEach
    void setUp() {
        personalLoanScheme = new LoanScheme(
                "SCHEME-PL-01",
                LoanType.PERSONAL_LOAN,
                "Prime Personal Loan",
                new BigDecimal("10000.00"),
                new BigDecimal("1000000.00"),
                6,
                60,
                new BigDecimal("11.50"),
                true
        );

        homeLoanScheme = new LoanScheme(
                "SCHEME-HL-01",
                LoanType.HOME_LOAN,
                "Dream Home Mortgage",
                new BigDecimal("500000.00"),
                new BigDecimal("50000000.00"),
                36,
                360,
                new BigDecimal("8.40"),
                true
        );
    }

    @Test
    @DisplayName("Loan Scheme: Get all active schemes")
    void testGetActiveSchemes() {
        when(schemeRepository.findByIsActiveTrue()).thenReturn(List.of(personalLoanScheme, homeLoanScheme));

        List<LoanSchemeDTO> schemes = schemeService.getActiveSchemes();

        assertEquals(2, schemes.size());
        assertEquals("SCHEME-PL-01", schemes.get(0).schemeId());
        assertEquals(LoanType.PERSONAL_LOAN, schemes.get(0).loanType());
        assertEquals("SCHEME-HL-01", schemes.get(1).schemeId());
        assertEquals(LoanType.HOME_LOAN, schemes.get(1).loanType());
    }

    @Test
    @DisplayName("Loan Scheme: Get scheme by ID when exists")
    void testGetSchemeById_Found() {
        when(schemeRepository.findById("SCHEME-PL-01")).thenReturn(Optional.of(personalLoanScheme));

        Optional<LoanSchemeDTO> result = schemeService.getSchemeById("SCHEME-PL-01");

        assertTrue(result.isPresent());
        assertEquals("Prime Personal Loan", result.get().schemeName());
        assertEquals(new BigDecimal("11.50"), result.get().baseInterestRate());
    }

    @Test
    @DisplayName("Loan Scheme: Get scheme by ID returns empty when not found")
    void testGetSchemeById_NotFound() {
        when(schemeRepository.findById("SCHEME-UNKNOWN")).thenReturn(Optional.empty());

        Optional<LoanSchemeDTO> result = schemeService.getSchemeById("SCHEME-UNKNOWN");

        assertTrue(result.isEmpty());
    }
}
