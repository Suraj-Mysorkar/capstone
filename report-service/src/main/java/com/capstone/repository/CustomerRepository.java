package com.capstone.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.capstone.model.Customer;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
	
	 // Tracks customer signups grouped by year and month
    @Query(value = "SELECT FORMATDATETIME(Created_At, 'yyyy-MM') AS month_str, COUNT(Customer_ID) " +
                   "FROM Customers GROUP BY FORMATDATETIME(Created_At, 'yyyy-MM')", 
           nativeQuery = true)
    List<Object[]> getMonthlyCustomerAcquisitions();
}
