package com.pulsehub.controller;

import com.pulsehub.dto.response.IceServerResponse;
import com.pulsehub.service.CallSignalingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/calls")
@RequiredArgsConstructor
@Tag(name = "Calls")
public class CallController {

    private final CallSignalingService callSignalingService;

    @GetMapping("/ice-servers")
    public ResponseEntity<List<IceServerResponse>> getIceServers() {
        return ResponseEntity.ok(callSignalingService.getIceServers());
    }

}
