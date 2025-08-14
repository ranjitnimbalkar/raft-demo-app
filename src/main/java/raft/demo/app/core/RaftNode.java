package raft.demo.app.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import raft.demo.app.dto.AppendEntries;
import raft.demo.app.dto.AppendEntriesResp;
import raft.demo.app.dto.RequestVote;
import raft.demo.app.dto.RequestVoteResp;
import raft.demo.app.net.PeerClient;
import raft.demo.app.state.RaftState;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class RaftNode {

    private static final Logger log = LoggerFactory.getLogger(RaftNode.class);

    private final ScheduledExecutorService raftExec;
    private final RaftState state = new RaftState();
    private final PeerClient peerClient;
    private final String nodeId;
    private final List<String> peers;
    private final int electionTimeoutMin;
    private final int electionTimeoutMax;
    private final int heartbeatIntervalMs;
    private final int rpcTimeoutMs;

    // internal
    private volatile long electionTimeoutDeadline = 0;
    private final Random random = new Random();
    private final Map<String, Integer> votesReceived = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(true);

    public RaftNode(org.springframework.core.env.Environment env, PeerClient peerClient) {
        this.peerClient = peerClient;
        this.nodeId = env.getProperty("raft.nodeId", "NODE");
        this.peers = Optional.ofNullable(env.getProperty("raft.peers"))
                .map(s -> Arrays.asList(s.split(",")))
                .orElseGet(ArrayList::new);

        this.electionTimeoutMin = Integer.parseInt(env.getProperty("raft.electionTimeoutMsMin", "150"));
        this.electionTimeoutMax = Integer.parseInt(env.getProperty("raft.electionTimeoutMsMax", "300"));
        this.heartbeatIntervalMs = Integer.parseInt(env.getProperty("raft.heartbeatIntervalMs", "50"));
        this.rpcTimeoutMs = Integer.parseInt(env.getProperty("raft.rpcTimeoutMs", "300"));

        this.raftExec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "raft-core-" + nodeId);
            t.setDaemon(true);
            return t;
        });

        // schedule election timer checker
        this.resetElectionTimeout();
        raftExec.scheduleAtFixedRate(this::tickElection, 20, 20, TimeUnit.MILLISECONDS);

        // heartbeat scheduler for leader
        raftExec.scheduleAtFixedRate(this::onHeartbeatTick, heartbeatIntervalMs, heartbeatIntervalMs, TimeUnit.MILLISECONDS);

        log.info("RaftNode {} started with peers {}", nodeId, peers);
    }

    // PUBLIC API: used by controllers to enqueue requests
    public void submit(Runnable task) {
        if (!running.get()) throw new RejectedExecutionException("Raft node stopped");
        raftExec.execute(task);
    }

    public RaftState getState() { return state; }

    // ---------- Election timer logic ----------
    private void resetElectionTimeout() {
        int timeout = electionTimeoutMin + random.nextInt(Math.max(1, electionTimeoutMax - electionTimeoutMin + 1));
        electionTimeoutDeadline = System.currentTimeMillis() + timeout;
    }

    private void tickElection() {
        // runs on raftExec thread
        final long now = System.currentTimeMillis();
        if (state.getRole() != RaftState.Role.LEADER && now >= electionTimeoutDeadline) {
            startElection();
        }
    }

    private void startElection() {
        state.setRole(RaftState.Role.CANDIDATE);
        long newTerm = state.getCurrentTerm() + 1;
        state.setCurrentTerm(newTerm);
        state.setVotedFor(nodeId);
        votesReceived.clear();
        votesReceived.put(nodeId, 1);
        resetElectionTimeout();
        log.info("[{}] Starting election for term {}", nodeId, newTerm);

        RequestVote rv = new RequestVote(newTerm, nodeId, state.lastLogIndex(), state.lastLogTerm());

        // send votes async to peers
        for (String peer : peers) {
            peerClient.requestVote(peer, rv)
                    .timeout(java.time.Duration.ofMillis(rpcTimeoutMs))
                    .subscribe(resp -> submit(() -> handleRequestVoteResponse(peer, rv, resp)),
                            err -> {
                                // treat as no-vote
                                log.debug("requestVote error to {}: {}", peer, err.getMessage());
                            });
        }
    }

    private void handleRequestVoteResponse(String peer, RequestVote req, RequestVoteResp resp) {
        // runs on raftExec thread because we re-enter via submit in subscribe above
        if (resp == null) return;
        if (resp.term() > state.getCurrentTerm()) {
            state.setCurrentTerm(resp.term());
            state.setRole(RaftState.Role.FOLLOWER);
            state.setVotedFor(null);
            resetElectionTimeout();
            return;
        }
        if (resp.voteGranted()) {
            votesReceived.put(peer, 1);
            long votes = votesReceived.values().stream().mapToInt(Integer::intValue).sum();
            int majority = (peers.size() + 1) / 2 + 1; // peers + self
            if (votes >= majority && state.getRole() == RaftState.Role.CANDIDATE) {
                becomeLeader();
            }
        }
    }

    private void becomeLeader() {
        state.setRole(RaftState.Role.LEADER);
        log.info("[{}] Became leader for term {}", nodeId, state.getCurrentTerm());
        // On becoming leader, initialize leader state (nextIndex, matchIndex) in real impl
        // send initial heartbeat
        sendHeartbeats();
    }

    // ---------- Heartbeats ----------
    private void onHeartbeatTick() {
        if (state.getRole() == RaftState.Role.LEADER) {
            sendHeartbeats();
        }
    }

    private void sendHeartbeats() {
        AppendEntries heartbeat = new AppendEntries(state.getCurrentTerm(), nodeId, state.lastLogIndex(),
                state.lastLogTerm(), Collections.emptyList(), state.getCommitIndex());
        for (String peer : peers) {
            peerClient.appendEntries(peer, heartbeat)
                    .timeout(java.time.Duration.ofMillis(rpcTimeoutMs))
                    .subscribe(resp -> submit(() -> handleAppendEntriesResp(peer, heartbeat, resp)),
                            err -> log.debug("appendEntries error to {}: {}", peer, err.getMessage()));
        }
    }

    private void handleAppendEntriesResp(String peer, AppendEntries req, AppendEntriesResp resp) {
        if (resp == null) return;
        if (resp.term() > state.getCurrentTerm()) {
            state.setCurrentTerm(resp.term());
            state.setRole(RaftState.Role.FOLLOWER);
            state.setVotedFor(null);
            resetElectionTimeout();
            return;
        }
        // success handling, update matchIndex/nextIndex in a full impl
    }

    // ---------- Incoming RPC handlers (called by controllers) ----------
    public void onAppendEntries(AppendEntries req, CompletableFuture<AppendEntriesResp> future) {
        // Called by controller via submit() so runs on raftExec
        if (req.term() < state.getCurrentTerm()) {
            future.complete(new AppendEntriesResp(state.getCurrentTerm(), false));
            return;
        }
        // update term & role if needed
        if (req.term() > state.getCurrentTerm()) {
            state.setCurrentTerm(req.term());
            state.setVotedFor(null);
            state.setRole(RaftState.Role.FOLLOWER);
        }
        // reset election timeout on valid heartbeat
        resetElectionTimeout();

        // validate prevLogIndex/prevLogTerm etc. (simplified)
        boolean ok = true; // TODO: implement actual log consistency checks and append
        if (!req.entries().isEmpty()) {
            for (var e : req.entries()) state.appendLog(e);
        }
        // advance commitIndex if leaderCommit > commitIndex
        if (req.leaderCommit() > state.getCommitIndex()) {
            state.setCommitIndex((int) Math.min(req.leaderCommit(), state.lastLogIndex()));
        }
        future.complete(new AppendEntriesResp(state.getCurrentTerm(), ok));
    }

    public void onRequestVote(RequestVote req, CompletableFuture<RequestVoteResp> future) {
        if (req.term() < state.getCurrentTerm()) {
            future.complete(new RequestVoteResp(state.getCurrentTerm(), false));
            return;
        }

        if (req.term() > state.getCurrentTerm()) {
            state.setCurrentTerm(req.term());
            state.setVotedFor(null);
            state.setRole(RaftState.Role.FOLLOWER);
        }

        boolean candidateUpToDate = (req.lastLogTerm() > state.lastLogTerm())
                || (req.lastLogTerm() == state.lastLogTerm() && req.lastLogIndex() >= state.lastLogIndex());

        synchronized (state) { // small sync to safely grant vote
            if ((state.getVotedFor() == null || state.getVotedFor().equals(req.candidateId())) && candidateUpToDate) {
                state.setVotedFor(req.candidateId());
                resetElectionTimeout();
                future.complete(new RequestVoteResp(state.getCurrentTerm(), true));
                return;
            }
        }

        future.complete(new RequestVoteResp(state.getCurrentTerm(), false));
    }

    // shutdown
    public void stop() {
        running.set(false);
        raftExec.shutdownNow();
    }
}
