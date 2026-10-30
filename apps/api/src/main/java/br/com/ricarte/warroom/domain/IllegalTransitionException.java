package br.com.ricarte.warroom.domain;

/** A state change the machine does not allow. Mapped to HTTP 409 by the web layer. */
public class IllegalTransitionException extends RuntimeException {

    private final String subject;
    private final String from;
    private final String to;

    public IllegalTransitionException(String subject, String from, String to) {
        super(subject + " cannot move from " + from + " to " + to);
        this.subject = subject;
        this.from = from;
        this.to = to;
    }

    public String getSubject() {
        return subject;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }
}
