{ lib
  , fetchFromGitHub
  , fetchYarnDeps
  , mkYarnPackage
  , callPackage
  , cargo-tauri
  , dpkg
  , cmake
  , dbus
  , freetype
  , gtk3
  , libsoup
  , openssl
  , pkg-config
  , webkitgtk
  }:
let
  fs = lib.fileset;
  version = "nix-build";
  name = "filespider";
  source = fetchFromGitHub {
    owner = "Kruemmelspalter";
    repo = name;
    rev = version;
    hash = "sha256-nPBu2lfltd/EthfV3GIOl+Zj8gqQR0jZ6dZgnOAS55E=";  
  };
  frontend-build = mkYarnPackage rec {
    src = source;

    offlineCache = fetchYarnDeps {
     yarnLock = src + "/yarn.lock";
     sha256 = "sha256-ts5WW422nUboTRp29jZ58gx+5aIPcPz/jjzR3Wo5zZU=";
    };

    packageJSON =  src + "/package.json";
    buildPhase = ''
     export HOME=$(mktemp -d)
     yarn run --offline build
     cp -r deps/filespider/dist $out
    '';
    distPhase = "true";
    dontInstall = true;
    };
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

  buildPhase = ''
    ${cargo-tauri.outPath}/bin/cargo-tauri build -b deb \
      -c '{"build" : {"distDir": "${frontend-build.outPath}", "beforeBuildCommand": "true"}}'
  '';

  installPhase = ''
    ${dpkg.outPath}/bin/dpkg-deb -x target/release/bundle/deb/*.deb $out
    mv $out/usr/* $out
    mv $out/bin/migrator $out/bin/filespider-migrate
  '';

}
