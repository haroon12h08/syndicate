package com.syndicate.company;

import com.syndicate.company.dto.CompanyDto;
import com.syndicate.company.dto.CreateCompanyRequest;
import com.syndicate.company.dto.UpdateCompanyRequest;
import com.syndicate.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping
    public List<CompanyDto> list(@AuthenticationPrincipal User currentUser) {
        return companyService.listForUser(currentUser.getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyDto create(@Valid @RequestBody CreateCompanyRequest request,
                              @AuthenticationPrincipal User currentUser) {
        return companyService.create(request, currentUser.getId());
    }

    @GetMapping("/{id}")
    public CompanyDto get(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return companyService.get(id, currentUser.getId());
    }

    @PutMapping("/{id}")
    public CompanyDto update(@PathVariable UUID id, @Valid @RequestBody UpdateCompanyRequest request,
                              @AuthenticationPrincipal User currentUser) {
        return companyService.update(id, request, currentUser.getId());
    }
}
