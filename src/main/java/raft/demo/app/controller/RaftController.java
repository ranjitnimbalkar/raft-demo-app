package raft.demo.app.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import raft.demo.app.dto.*;
import raft.demo.app.core.RaftNode;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/raft")
public class RaftController {

    private final RaftNode node;

    @Autowired
    public RaftController(RaftNode node) { this.node = node; }

    @PostMapping("/appendEntries")
    public CompletableFuture<ResponseEntity<AppendEntriesResp>> appendEntries(@RequestBody AppendEntries req) {
        var future = new CompletableFuture<AppendEntriesResp>();
        node.submit(() -> node.onAppendEntries(req, future));
        return future.thenApply(ResponseEntity::ok);
    }

    @PostMapping("/requestVote")
    public CompletableFuture<ResponseEntity<RequestVoteResp>> requestVote(@RequestBody RequestVote req) {
        var future = new CompletableFuture<RequestVoteResp>();
        node.submit(() -> node.onRequestVote(req, future));
        return future.thenApply(ResponseEntity::ok);
    }
}

