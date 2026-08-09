package com.example.jylos.git;

/** Result of running a git subprocess via {@link GitProcessRunner}. */
record Proc(int code, String out, String err) {
    boolean success() {
        return code == 0;
    }

    String detail() {
        String e = err != null ? err.trim() : "";
        return e.isEmpty() ? (out != null ? out.trim() : "") : e;
    }
}
