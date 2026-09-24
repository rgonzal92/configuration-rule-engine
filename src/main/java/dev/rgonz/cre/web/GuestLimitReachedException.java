package dev.rgonz.cre.web;

/** Signals that the rolling-hour limit on new guest workspaces has been reached. */
public class GuestLimitReachedException extends RuntimeException {
  public GuestLimitReachedException() {
    super("Guest workspace limit reached");
  }
}
