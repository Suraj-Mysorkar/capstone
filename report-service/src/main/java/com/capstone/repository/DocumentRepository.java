package com.capstone.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.capstone.model.Document;
import com.capstone.model.User;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
}
