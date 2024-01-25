with import <nixpkgs> { };
mkShell {
  nativeBuildInputs = with pkgs; [
    rustup
    cargo-tauri

    mold
    
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
