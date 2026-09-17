package com.tracker.expense.controller;

import com.tracker.expense.model.User;
import com.tracker.expense.security.SecurityUtils;
import com.tracker.expense.service.AiChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    @Autowired
    private AiChatService aiChatService;

    @PostMapping("/chat")
    public ResponseEntity<?> processChatQuery(@RequestBody Map<String, String> payload) {
        User user = SecurityUtils.getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized access."));
        }

        String query = payload.get("message");
        if (!StringUtils.hasText(query)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Query message cannot be empty."));
        }

        Map<String, Object> result = aiChatService.processUserQuery(user, query.trim());
        return ResponseEntity.ok(result);
    }
}
