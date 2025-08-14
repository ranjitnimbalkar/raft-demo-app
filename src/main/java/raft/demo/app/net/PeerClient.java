package raft.demo.app.net;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import raft.demo.app.dto.*;

import java.time.Duration;

@Component
public class PeerClient {

    private static final Logger log = LoggerFactory.getLogger(PeerClient.class);
    private final WebClient web;

    public PeerClient(WebClient.Builder builder) {
        this.web = builder.build();
    }

    public Mono<RequestVoteResp> requestVote(String baseUrl, RequestVote req) {
        return web.post()
                .uri(baseUrl + "/raft/requestVote")
                .bodyValue(req)
                .retrieve()
                .bodyToMono(RequestVoteResp.class)
                .doOnError(e -> log.debug("requestVote error: {}", e.getMessage()))
                .onErrorResume(e -> Mono.just(new RequestVoteResp(req.term(), false)));
    }

    public Mono<AppendEntriesResp> appendEntries(String baseUrl, AppendEntries req) {
        return web.post()
                .uri(baseUrl + "/raft/appendEntries")
                .bodyValue(req)
                .retrieve()
                .bodyToMono(AppendEntriesResp.class)
                .doOnError(e -> log.debug("appendEntries error: {}", e.getMessage()))
                .onErrorResume(e -> Mono.just(new AppendEntriesResp(req.term(), false)));
    }
}
