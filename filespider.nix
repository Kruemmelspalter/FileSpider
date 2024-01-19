{ cmake
  , dbus
  , fetchFromGitHub
  , fetchYarnDeps
  , freetype
  , gtk3
  , libsoup
  , mkYarnPackage
  , openssl
  , pkg-config
  , webkitgtk,
  stdenv, lib, callPackage, cargo-tauri, dpkg }:
let
  fs = lib.fileset;
  version = "nix-build";
  name = "filespider";
  source = fetchFromGitHub {
    owner = "Kruemmelspalter";
    repo = name;
    rev = version;
    hash = lib.fakeSha256    
  };
  frontend-builder = callPackage ./frontend.nix {};
  frontend-build = frontend-builder source;
  mkRustPlatform = callPackage ./rust-nightly.nix {};
  rustPlatform = mkRustPlatform {
    date = "2024-01-01";
    channel = "nightly";
  };
in
rustPlatform.buildRustPackage rec {
  inherit version name;
  pname = name;
  src = source + "/src-tauri";

  buildInputs = [ dbus openssl freetype libsoup gtk3 webkitgtk cmake cargo-tauri dpkg ];
  nativeBuildInputs = [ pkg-config ];

  lockFile = src + "/Cargo.lock";

  cargoLock = {
    inherit lockFile;
  };

  patchPhase = ''
    ls ${frontend-build.outPath}

  '';

  buildPhase = ''
    ${cargo-tauri.outPath}/bin/cargo-tauri build -b deb \
      -c '{"build" : {"distDir": "${frontend-build.outPath}", "beforeBuildCommand": "true"}}'
  '';

  installPhase = ''
    ${dpkg.outPath}/bin/dpkg-deb -x target/release/bundle/deb/*.deb $out
    mv $out/usr/bin $out/bin
  '';

}
