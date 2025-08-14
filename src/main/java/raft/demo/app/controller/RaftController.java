package raft.demo.app.controller;
import org.springframework.web.bind.annotation.*;
import raft.demo.app.component.RaftNode;
import raft.demo.app.state.LogEntry;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/raft")
public class RaftController {
    private final RaftNode node;

    public RaftController(RaftNode node) { this.node = node; }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "nodeId", node.getNodeId(),
                "role", node.getRole().name(),
                "term", node.getCurrentTerm(),
                "peers", node.getPeerList()
        );
    }

    @GetMapping("/requestVote")
    public boolean requestVote(@RequestParam long term, @RequestParam String candidateId) {
        return node.handleRequestVote(term, candidateId);
    }

    @PostMapping("/appendEntries")
    public boolean appendEntries(@RequestParam long term, @RequestParam String leaderId, @RequestBody(required = false) List<LogEntry> entries, @RequestParam int leaderCommit) {
        return node.handleAppendEntries(term, leaderId, entries, leaderCommit);
    }
}