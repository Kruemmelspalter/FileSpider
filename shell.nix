with import <nixpkgs> { };
mkShell {
  nativeBuildInputs = with pkgs; [
    rustup
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

    xournalpp
    calibre
  ];
  PATH = "${builtins.getEnv "PATH"}:${builtins.getEnv "HOME"}/.cargo/bin";
}
