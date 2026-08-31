package com.afriland.audiences.service.core;

import com.afriland.audiences.dto.core.TemplateDTO;
import com.afriland.audiences.model.core.Template;

import java.util.List;

public interface TemplateService {

    Template createTemplate(TemplateDTO dto);

    Template updateTemplate(TemplateDTO dto);

    Template getTemplateById(Long id);
    
    List<Template> getTemplates();

    void deleteTemplate(Long id);
    
    //A completer avec d'autres services si necessaire
}
