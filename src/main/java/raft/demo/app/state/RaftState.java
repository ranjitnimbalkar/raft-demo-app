package raft.demo.app.state;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RaftState {

    public enum Role {FOLLOWER, CANDIDATE, LEADER}

    private volatile long currentTerm;
    private volatile String votedFor;
    private volatile Role role = Role.FOLLOWER;
    private final List<LogEntry> logEntries = new ArrayList<>();
    private final AtomicInteger commitIndex = new AtomicInteger(0);

    public long getCurrentTerm() {
        return currentTerm;
    }

    public synchronized void setCurrentTerm(long currentTerm) {
        this.currentTerm = currentTerm;
    }

    public String getVotedFor() {
        return votedFor;
    }

    public synchronized void setVotedFor(String votedFor) {
        this.votedFor = votedFor;
    }

    public Role getRole() {
        return role;
    }

    public synchronized void setRole(Role role) {
        this.role = role;
    }

    public List<LogEntry> getLogEntries() {
        return logEntries;
    }

    public AtomicInteger getCommitIndex() {
        return commitIndex;
    }
}
