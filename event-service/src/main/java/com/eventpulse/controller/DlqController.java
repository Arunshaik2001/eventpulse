package com.eventpulse.controller;

import com.eventpulse.Event;
import com.eventpulse.service.DlqReplayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dlq")
@RequiredArgsConstructor
public class DlqController {

    private final DlqReplayService replayService;

    @PostMapping("/replay")
    public ResponseEntity<String> replay(@RequestBody Event event) {

        replayService.replay(event);

        return ResponseEntity.ok("Event replayed");

    }

}
