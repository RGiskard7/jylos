# Jylos for Nix/NixOS, from the .deb of a GitHub release (x86_64 Linux).
#
# The .deb is the jpackage image the other Linux packages use: Jylos's JAR, its plugins and a
# Java runtime with JavaFX. Its native binaries are patched for Nix (autoPatchelfHook) and the
# libraries JavaFX opens at run time (GTK, OpenGL, X11) are put on its library path. JavaFX has
# no native Wayland backend: under a Wayland compositor it runs through XWayland.
{
  lib,
  stdenv,
  fetchurl,
  dpkg,
  autoPatchelfHook,
  makeWrapper,
  alsa-lib,
  freetype,
  fontconfig,
  zlib,
  glib,
  gtk3,
  pango,
  cairo,
  gdk-pixbuf,
  atk,
  libGL,
  xorg,
  # Their current names; an older nixpkgs only has them under xorg.
  libx11 ? xorg.libX11,
  libxtst ? xorg.libXtst,
  libxxf86vm ? xorg.libXxf86vm,
  libxi ? xorg.libXi,
  libxrender ? xorg.libXrender,
  libxext ? xorg.libXext,
}:

let
  # What JavaFX loads by name when it starts (dlopen), so autoPatchelf cannot see it.
  runtimeLibs = [
    glib
    gtk3
    pango
    cairo
    gdk-pixbuf
    atk
    libGL
    freetype
    fontconfig
    libx11
    libxtst
    libxxf86vm
    libxi
    libxrender
    libxext
  ];
in
stdenv.mkDerivation rec {
  pname = "jylos";
  version = "2.7.0";

  src = fetchurl {
    url = "https://github.com/RGiskard7/jylos/releases/download/v${version}/jylos-linux-amd64.deb";
    hash = "sha256-562QSma47pQa1iyIfyDEF1a2MrAl0rDb5AKQsNDg9Xg=";
  };

  nativeBuildInputs = [
    dpkg
    autoPatchelfHook
    makeWrapper
  ];

  buildInputs = [
    alsa-lib
    zlib
    stdenv.cc.cc.lib
  ] ++ runtimeLibs;

  unpackPhase = ''
    runHook preUnpack
    dpkg-deb -x $src .
    runHook postUnpack
  '';

  installPhase = ''
    runHook preInstall
    mkdir -p $out/opt $out/bin
    cp -r opt/jylos $out/opt/jylos
    # JavaFX draws only through X11: under Wayland it must go through XWayland, even when the
    # session asks GTK for its Wayland backend.
    makeWrapper $out/opt/jylos/bin/Jylos $out/bin/jylos \
      --prefix LD_LIBRARY_PATH : ${lib.makeLibraryPath runtimeLibs} \
      --set GDK_BACKEND x11

    install -Dm644 opt/jylos/lib/Jylos.png $out/share/pixmaps/jylos.png
    mkdir -p $out/share/applications
    cat > $out/share/applications/jylos.desktop <<DESKTOP
    [Desktop Entry]
    Name=Jylos
    Comment=Local-first Markdown notes
    Exec=jylos
    Icon=jylos
    Terminal=false
    Type=Application
    Categories=Office;Utility;TextEditor;
    StartupWMClass=com.example.jylos.Main
    DESKTOP
    runHook postInstall
  '';

  meta = {
    description = "Local-first Markdown notes with wiki-links, graph, Kanban, Canvas and Git sync";
    homepage = "https://github.com/RGiskard7/jylos";
    license = lib.licenses.mit;
    platforms = [ "x86_64-linux" ];
    mainProgram = "jylos";
    sourceProvenance = [ lib.sourceTypes.binaryNativeCode ];
  };
}
