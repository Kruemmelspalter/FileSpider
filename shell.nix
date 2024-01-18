with import <nixpkgs> { };
let
  filespider = pkgs.callPackage ./filespider.nix {};
in
mkShell {
  nativeBuildInputs = with pkgs; [
    filespider
    rustup
    cargo-tauri
    
    pkg-config
    dbus
    openssl
    freetype
    libsoup
    gtk3
    webkitgtk
    cmake

    nodejs
    yarn

    sqlite-interactive
    nixfmt
    wget

    vscodium

    xournalpp
    calibre
  ];
  PATH = "${builtins.getEnv "PATH"}:${builtins.getEnv "HOME"}/.cargo/bin";
}
