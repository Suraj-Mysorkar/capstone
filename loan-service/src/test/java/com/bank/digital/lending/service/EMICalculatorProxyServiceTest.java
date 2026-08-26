package com.bank.digital.lending.service;

import com.bank.digital.lending.model.dto.EMICalculationRequest;
import com.bank.digital.lending.model.dto.EMICalculationResponse;
import com.bank.digital.lending.model.entity.LoanScheme;
import com.bank.digital.lending.model.enums.LoanType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EMICalculatorProxyServiceTest {

    @Mock
    private LoanSchemeService schemeService;

    private EMICalculatorProxyService emiCalculator;

    @BeforeEach
    void setUp() {
        emiCalculator = new EMICalculatorProxyService(schemeService);
    }

    @Test
    @DisplayName("Calculate EMI: Standard 100,000 at 12% for 12 months")
    void testComputeMonthlyEMI() {
        BigDecimal principal = new BigDecimal("100000.00");
        BigDecimal rate = new BigDecimal("12.00");
        int tenure = 12;

        BigDecimal emi = emiCalculator.computeMonthlyEMI(principal, rate, tenure);

        assertNotNull(emi);
        // Formula output for 100k @ 12% for 12m is ~8884.88
        assertEquals(new BigDecimal("8884.88"), emi);
    }

    @Test
    @DisplayName("Full Calculation Response with Amortization Schedule")
    void testCalculateEMI_FullResponse() {
        EMICalculationRequest request = new EMICalculationRequest(
                new BigDecimal("500000.00"),
                60,
                new BigDecimal("9.50"),
                null
        );

        EMICalculationResponse response = emiCalculator.calculateEMI(request);

        assertNotNull(response);
        assertEquals(new BigDecimal("500000.00"), response.principalAmount());
        assertEquals(60, response.tenureMonths());
        assertEquals(new BigDecimal("9.50"), response.annualInterestRate());
        assertEquals(new BigDecimal("10500.93"), response.monthlyEMI());
        assertTrue(response.totalInterestPayable().compareTo(BigDecimal.ZERO) > 0);
        assertEquals(60, response.amortizationSchedule().size());

        // Validate first and last month entries
        assertEquals(1, response.amortizationSchedule().get(0).monthNumber());
        assertEquals(60, response.amortizationSchedule().get(59).monthNumber());
        assertEquals(BigDecimal.ZERO, response.amortizationSchedule().get(59).endingBalance());
    }

    @Test
    @DisplayName("Calculate EMI using Scheme ID Lookup")
    void testCalculateEMI_WithSchemeId() {
        LoanScheme scheme = new LoanScheme(
                "SCHEME-PL-01",
                LoanType.PERSONAL_LOAN,
                "Prime Personal",
                new BigDecimal("10000.00"),
                new BigDecimal("1000000.00"),
                6,
                60,
                new BigDecimal("11.50"),
                true
        );
        when(schemeService.getSchemeEntityById("SCHEME-PL-01")).thenReturn(Optional.of(scheme));

        EMICalculationRequest request = new EMICalculationRequest(
                new BigDecimal("200000.00"),
                24,
                null,
                "SCHEME-PL-01"
        );

        EMICalculationResponse response = emiCalculator.calculateEMI(request);

        assertNotNull(response);
        assertEquals(new BigDecimal("11.50"), response.annualInterestRate());
        assertEquals(new BigDecimal("9368.06"), response.monthlyEMI());
    }
}
