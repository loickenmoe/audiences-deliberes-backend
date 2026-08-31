package com.afriland.audiences.controller.core;

import org.springframework.web.bind.annotation.*;


@RequestMapping("${api.prefix}/templates") // Chemin de base pour toutes les méthodes
@RestController
public class TemplateController {

    @GetMapping // GET /templates
    public String getTemplates() {
        return "Liste des templates";
    }

    @GetMapping("/{id}") // GET /templates/{id}
    public String getTemplateById(@PathVariable Long id) {
        return "Template avec ID : " + id;
    }

    @PostMapping // POST /templates
    public String createTemplate() {
        return "Template créé";
    }

    @PutMapping("/{id}") // PUT /templates/{id}
    public String updateTemplate(@PathVariable Long id) {
        return "Template mis à jour : " + id;
    }

    @DeleteMapping("/{id}") // DELETE /templates/{id}
    public String deleteTemplate(@PathVariable Long id) {
        return "Template supprimé : " + id;
    }
}
