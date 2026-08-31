package com.afriland.audiences.service;

// Core Models (Assuming package structure)
// Security Models

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // Most dashboard methods are read-only
public class DashboardServiceImpl implements DashboardService {

    }