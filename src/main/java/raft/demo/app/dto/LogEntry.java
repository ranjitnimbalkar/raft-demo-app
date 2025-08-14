package raft.demo.app.dto;

public class LogEntry {
    private final long index;
    private final long term;
    private final String command;

    public LogEntry(long index, long term, String command) {
        this.index = index;
        this.term = term;
        this.command = command;
    }

    public long index() { return index; }
    public long term() { return term; }
    public String command() { return command; }
}