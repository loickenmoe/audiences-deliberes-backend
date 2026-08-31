package com.afriland.audiences.repository.core;

import com.afriland.audiences.model.core.Template;
import org.springframework.data.jpa.repository.JpaRepository;

//A modifier
public interface TemplateRepository extends JpaRepository<Template, Integer> {
}
