# Jylos for Nix and NixOS

A flake that packages the `.deb` of a GitHub release for `x86_64-linux`: the same jpackage image
as the other Linux packages (Jylos, its plugins and a Java runtime with JavaFX), patched for Nix.

```bash
# Try it
nix run "github:RGiskard7/jylos?dir=packaging/nix"
# Install it in your profile
nix profile install "github:RGiskard7/jylos?dir=packaging/nix"
```

On NixOS, as a flake input:

```nix
inputs.jylos.url = "github:RGiskard7/jylos?dir=packaging/nix";
# …
environment.systemPackages = [ inputs.jylos.packages.x86_64-linux.default ];
```

- **Data:** in `~/.local/share/jylos`, logs in `~/.local/state/jylos/logs`.
- **Wayland:** JavaFX draws only through X11, so under a Wayland compositor Jylos runs through
  XWayland (the launcher sets `GDK_BACKEND=x11`); XWayland must be enabled.
- **Updates:** come with the flake, not from the app: its in-app updater finds no package for
  NixOS. Each release opens a pull request that points `jylos.nix` at its `.deb` (the
  `nix-package` job of `release-jylos.yml`); `nix.yml` builds the flake on every change here.
- **Only x86_64:** releases have no ARM Linux build yet.
- **Tested** by building it and starting Jylos in the `nixos/nix` Docker image (x86_64) on a
  virtual X display (Xvfb). Not tested yet on a real NixOS desktop or under Wayland.
