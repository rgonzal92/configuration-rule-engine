package dev.rgonz.cre.workspace;

/** Signals that the rolling-hour limit on new guest workspaces has been reached. */
class GuestLimitReachedException extends RuntimeException {
  public GuestLimitReachedException() {
    super("Guest workspace limit reached");
  }
}
