package dev.getelements.elements.crossfire.model.error;

public class DuplicateConnectionException extends IllegalStateException {

  public DuplicateConnectionException() {}

  public DuplicateConnectionException(String s) {
    super(s);
  }

  public DuplicateConnectionException(String message, Throwable cause) {
    super(message, cause);
  }

  public DuplicateConnectionException(Throwable cause) {
    super(cause);
  }

}
