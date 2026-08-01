package com.example.shadowrouter.controller;

import java.util.List;

import com.example.shadowrouter.service.MismatchTraceService;
import com.example.shadowrouter.trace.MismatchTrace;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read API for persisted primary/candidate mismatches (debugging / visualization).
 */
@RestController
public class TraceController {

    private final MismatchTraceService mismatchTraceService;

    public TraceController(MismatchTraceService mismatchTraceService) {
        this.mismatchTraceService = mismatchTraceService;
    }

    @GetMapping("/traces")
    public List<MismatchTrace> recentTraces(
            @RequestParam(defaultValue = "50") int limit) {
        int capped = Math.min(Math.max(limit, 1), 500);
        return mismatchTraceService.recentTraces(capped);
    }
}
