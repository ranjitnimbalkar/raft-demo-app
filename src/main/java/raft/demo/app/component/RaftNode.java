package raft.demo.app.component;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import raft.demo.app.state.LogEntry;
import raft.demo.app.state.RaftState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class RaftNode {

    private final RaftState state = new RaftState();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Random random = new Random();


    @Value("${raft.nodeId}")
    private String nodeId;

    @Value("${raft.peers}")
    private String peers; // comma separated host:port list

    private List<String> peerList = new ArrayList<>();

    private volatile long lastHeartbeat = Instant.now().toEpochMilli();

    private final PeerClient peerClient;

    public RaftNode(PeerClient peerClient) {
        this.peerClient = peerClient;
    }

    @PostConstruct
    public void init() {
        start();
        System.out.println("Node started: " + this.getNodeId());
    }

    public void start() {
        if(peers != null && !peers.isEmpty()) {
           peerList = List.of(peers.split(","));
        }
        scheduleElectionTimer();
        scheduleHeartbeatSender();
    }

    private void scheduleHeartbeatSender() {
        scheduler.scheduleAtFixedRate(() -> {
            if (state.getRole() == RaftState.Role.LEADER) sendHeartbeats();
        }, 0, 2, TimeUnit.SECONDS);
    }

    private void scheduleElectionTimer() {
        long electionTimeout = 4 + random.nextInt(5); // Randomized election timeout between 150ms and 300ms
        scheduler.schedule(this::electionTimeout, electionTimeout, TimeUnit.SECONDS);
    }

    private void electionTimeout() {
        long now = Instant.now().toEpochMilli();

        if (state.getRole() == RaftState.Role.LEADER) {
            scheduleElectionTimer();
            return;
        }

        if (now - lastHeartbeat < 150) {
            scheduleElectionTimer();
            return; // received heartbeat recently
        }

        // start election
        state.setRole(RaftState.Role.CANDIDATE);
        long newTerm = state.getCurrentTerm() + 1;
        state.setCurrentTerm(newTerm);
        state.setVotedFor(nodeId);

        int votes = 1; // vote for self
        for (String peer : peerList) {
            try {
                boolean granted = peerClient.requestVote(peer, newTerm, nodeId);
                if (granted) votes++;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        int majority = (peerList.size() + 1) / 2 + 1; // include self
        if (votes >= majority) {
            state.setRole(RaftState.Role.LEADER);
            System.out.println("[" + nodeId + "] Became leader for term " + newTerm);
            // as leader, immediately send heartbeats
            sendHeartbeats();
        } else {
            state.setRole(RaftState.Role.FOLLOWER);
        }

        scheduleElectionTimer();
    }

    private void sendHeartbeats() {
        for (String peer : peerList) {
            try {
                peerClient.appendEntries(peer, state.getCurrentTerm(), nodeId, Collections.emptyList(), state.getCommitIndex().get());
            } catch (Exception e) {
                // ignore
            }
        }
    }

    // RPC handlers invoked by controller
    public boolean handleRequestVote(long term, String candidateId) {
        if (term < state.getCurrentTerm()) return false;
        if (term > state.getCurrentTerm()) {
            state.setCurrentTerm(term);
            state.setVotedFor(null);
            state.setRole(RaftState.Role.FOLLOWER);
        }
        if (state.getVotedFor() == null || state.getVotedFor().equals(candidateId)) {
            state.setVotedFor(candidateId);
            return true;
        }
        return false;
    }

    public boolean handleAppendEntries(long term, String leaderId, List<LogEntry> entries, int leaderCommit) {
        if (term < state.getCurrentTerm()) return false;
        // accept leader
        if (term > state.getCurrentTerm()) state.setCurrentTerm(term);
        state.setRole(RaftState.Role.FOLLOWER);
        lastHeartbeat = Instant.now().toEpochMilli();
        // simplified: append entries naively
        if (entries != null && !entries.isEmpty()) {
            for (LogEntry e: entries) state.getLogEntries().add(e);
        }
        // commit
        if (leaderCommit > state.getCommitIndex().get()) state.getCommitIndex().getAndSet(leaderCommit);
        return true;
    }

    public String getNodeId() { return nodeId; }
    public RaftState.Role getRole() { return state.getRole(); }
    public long getCurrentTerm() { return state.getCurrentTerm(); }
    public List<String> getPeerList() { return peerList; }

}


