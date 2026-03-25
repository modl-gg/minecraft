package io.github._4drian3d.signedvelocity.shared.types;

public enum ResultType {
  ALLOWED("ALLOWED"),
  MODIFY("MODIFY"),
  CANCEL("CANCEL");

  private final String value;

  ResultType(final String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public static ResultType getOrThrow(final String type) {
    if ("ALLOWED".equals(type)) {
      return ALLOWED;
    } else if ("MODIFY".equals(type)) {
      return MODIFY;
    } else if ("CANCEL".equals(type)) {
      return CANCEL;
    } else {
      throw new IllegalArgumentException("Invalid result " + type);
    }
  }
}
