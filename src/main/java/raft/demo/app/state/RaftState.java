package raft.demo.app.state;


import raft.demo.app.dto.LogEntry;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class RaftState {

    public enum Role {FOLLOWER, CANDIDATE, LEADER}

    private final AtomicLong currentTerm = new AtomicLong(0);
    private volatile String votedFor = null;
    private volatile Role role = Role.FOLLOWER;
    private final List<LogEntry> logEntries = new CopyOnWriteArrayList<>();
    private final AtomicInteger commitIndex = new AtomicInteger(0);
    private volatile long lastApplied = 0;

    // atomic term
    public long getCurrentTerm() { return currentTerm.get(); }
    public void setCurrentTerm(long t) { currentTerm.set(t); }
    public boolean compareAndSetTerm(long expect, long update) { return currentTerm.compareAndSet(expect, update); }

    // votedFor is updated synchronously to avoid races when granting votes
    public synchronized String getVotedFor() { return votedFor; }
    public synchronized void setVotedFor(String v) { this.votedFor = v; }

    public Role getRole() { return role; }
    public synchronized void setRole(Role r) { this.role = r; }

    public List<LogEntry> getLogEntries() { return Collections.unmodifiableList(logEntries); }
    public void appendLog(LogEntry e) { logEntries.add(e); }
    public long lastLogIndex() { return logEntries.isEmpty() ? 0L : logEntries.get(logEntries.size()-1).index(); }
    public long lastLogTerm() { return logEntries.isEmpty() ? 0L : logEntries.get(logEntries.size()-1).term(); }

    public int getCommitIndex() { return commitIndex.get(); }
    public void setCommitIndex(int idx) { commitIndex.set(idx); }
    public long getLastApplied() { return lastApplied; }
    public void setLastApplied(long v) { this.lastApplied = v; }
}
