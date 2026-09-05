package com.capstone.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.capstone.model.Customer;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
	
	 // Tracks customer signups grouped by year and month
    @Query(value = "SELECT SUBSTRING(CONVERT(VARCHAR, created_at, 120), 1, 7) AS month_str, COUNT(id) " +
                   "FROM customers GROUP BY SUBSTRING(CONVERT(VARCHAR, created_at, 120), 1, 7)", 
           nativeQuery = true)
    List<Object[]> getMonthlyCustomerAcquisitions();
}
