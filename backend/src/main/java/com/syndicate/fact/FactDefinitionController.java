package com.syndicate.fact;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class FactDefinitionController {

    public record FactDefinitionDto(String factKey, String displayLabel, String valueType, boolean financial,
                                    String defaultMateriality) {
    }

    private final FactDefinitionRepository definitions;

    public FactDefinitionController(FactDefinitionRepository definitions) {
        this.definitions = definitions;
    }

    @GetMapping("/api/fact-definitions")
    public List<FactDefinitionDto> list() {
        return definitions.findAllByOrderByFactKeyAsc().stream()
                .map(d -> new FactDefinitionDto(d.getFactKey(), d.getDisplayLabel(), d.getValueType(),
                        d.isFinancial(), d.getDefaultMateriality()))
                .toList();
    }
}
