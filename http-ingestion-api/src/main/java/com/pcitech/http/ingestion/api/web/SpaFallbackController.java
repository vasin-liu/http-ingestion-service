package com.pcitech.http.ingestion.api.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaFallbackController {

    @GetMapping(value = {
            "/",
            "/connectors",
            "/connectors/**",
            "/stats"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
