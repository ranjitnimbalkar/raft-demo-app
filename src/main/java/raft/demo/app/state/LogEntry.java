package raft.demo.app.state;

public class LogEntry {

    private final long term;
    private final String command;

    public LogEntry(long term, String command) {
        this.term = term;
        this.command = command;
    }

    public long getTerm() {
        return term;
    }

    public String getCommand() {
        return command;
    }

    @Override
    public String toString() {
        return "LogEntry{" +
                "term=" + term +
                ", command='" + command + '\'' +
                '}';
    }
}
