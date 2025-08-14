package raft.demo.app.component;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import raft.demo.app.state.LogEntry;

import java.util.List;

@Component
public class PeerClient {

    private final RestTemplate rest = new RestTemplate();

    public boolean requestVote(String baseUrl, long term, String candidateId) {
        try {
            String url = "http://" + baseUrl + "/raft/requestVote?term=" + term + "&candidateId=" + candidateId;
            ResponseEntity<Boolean> r = rest.getForEntity(url, Boolean.class);
            return Boolean.TRUE.equals(r.getBody());
        } catch (Exception e) {
            return false;
        }
    }

    public boolean appendEntries(String baseUrl, long term, String leaderId, List<LogEntry> entries, int leaderCommit) {
        try {
            String url = "http://" + baseUrl + "/raft/appendEntries?term=" + term + "&leaderId=" + leaderId + "&leaderCommit=" + leaderCommit;
            ResponseEntity<Boolean> r = rest.postForEntity(url, entries, Boolean.class);
            return Boolean.TRUE.equals(r.getBody());
        } catch (Exception e) {
            return false;
        }
    }

}
