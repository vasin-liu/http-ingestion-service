package com.pcitech.http.ingestion.api.web;

import com.pcitech.http.ingestion.core.dto.TrialRequestDto;
import com.pcitech.http.ingestion.core.dto.TrialResponseDto;
import com.pcitech.http.ingestion.core.service.TrialRequestService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trial-requests")
public class TrialRequestController {

    private final TrialRequestService trialRequestService;

    public TrialRequestController(TrialRequestService trialRequestService) {
        this.trialRequestService = trialRequestService;
    }

    @PostMapping
    public TrialResponseDto trial(@Valid @RequestBody TrialRequestDto request) {
        return trialRequestService.execute(request);
    }
}
