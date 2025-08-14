package raft.demo.app.dto;

public record RequestVote(
        long term,
        String candidateId,
        long lastLogIndex,
        long lastLogTerm
) {}
