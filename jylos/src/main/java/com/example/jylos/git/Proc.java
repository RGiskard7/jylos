package com.example.jylos.git;

/**
 * Result of running a git subprocess via {@link GitProcessRunner}.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.5.0
 */
record Proc(int code, String out, String err) {
    boolean success() {
        return code == 0;
    }

    String detail() {
        String e = err != null ? err.trim() : "";
        return e.isEmpty() ? (out != null ? out.trim() : "") : e;
    }
}
