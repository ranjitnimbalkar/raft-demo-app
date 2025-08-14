package raft.demo.app.dto;

public record AppendEntriesResp(long term, boolean success) {}

