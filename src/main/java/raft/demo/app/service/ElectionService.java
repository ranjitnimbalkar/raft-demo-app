package raft.demo.app.service;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import raft.demo.app.component.RaftNode;

//@Service
public class ElectionService {

    private final RaftNode raftNode;

    public ElectionService(RaftNode raftNode) {
        this.raftNode = raftNode;
    }

    @PostConstruct
    public void init() {
        raftNode.start();
        System.out.println("Node started: " + raftNode.getNodeId());
    }

}
