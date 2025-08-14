package raft.demo.app.dto;

public record RequestVoteResp(long term, boolean voteGranted) {}
