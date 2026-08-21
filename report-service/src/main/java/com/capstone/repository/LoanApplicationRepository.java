package com.capstone.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.capstone.model.LoanApplication;

@Repository
public interface LoanApplicationRepository extends JpaRepository<LoanApplication, Long> {
	
	 // Groups application data counts by their status string
    @Query("SELECT l.status, COUNT(l) FROM LoanApplication l GROUP BY l.status")
    List<Object[]> countApplicationsByStatus();

    // Aggregates monthly application counts and financial totals using H2 compatible date formatting
    @Query(value = "SELECT FORMATDATETIME(Created_At, 'yyyy-MM') AS month_str, " +
                   "COUNT(Application_ID) AS app_count, " +
                   "SUM(Loan_Amount) AS total_vol " +
                   "FROM Loan_Applications GROUP BY FORMATDATETIME(Created_At, 'yyyy-MM')", 
           nativeQuery = true)
    List<Object[]> getMonthlyLoanMetrics();
}
